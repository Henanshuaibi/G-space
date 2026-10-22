package cn.g.space;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG = 0xfff7f8f4, INK = 0xff222b26, MUTED = 0xff737c73,
            ACCENT = 0xff4c6657, SOFT = 0xffe8eee5, LINE = 0xffe5e9e1, WHITE = 0xffffffff;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile int generation;
    private boolean resumed, loading, catalogLoaded;
    private String page = "home", query = "", catalogError;
    private int selectedDay = 6;
    private LinearLayout root, body;
    private TextView totalText, totalHint, pickerDone;
    private final Map<String, TextView> tileTimes = new HashMap<>();
    private List<AppCatalog.Entry> catalog = new ArrayList<>();
    private final Map<String, AppCatalog.Entry> entries = new HashMap<>();
    private AppStore store;
    private AppSelection selection;
    private UsageRepository.Data usage;
    private final LinkedHashSet<String> pending = new LinkedHashSet<>();
    private PickerAdapter pickerAdapter;
    private PopupMenu gMenu;
    private AppGrid appGrid;
    private ScrollView homeScroll;
    private TextView addButton;
    private ReorderSession dragSession;
    private View dragSource;
    private float dragX, dragY;
    private boolean dragInside, removeHovered, removeOnEnd;
    private final DragAutoScroll dragScroll = new DragAutoScroll(new DragAutoScroll.Host() {
        public void post(Runnable task, int delay) { main.postDelayed(task, delay); }
        public void remove(Runnable task) { main.removeCallbacks(task); }
        public boolean valid() { return resumed && dragSession != null && dragInside && homeScroll != null && !removeHovered; }
        public int height() { return homeScroll.getHeight(); }
        public int edge() { return dp(64); }
        public int maxStep() { return dp(15); }
        public void scrollBy(int delta) {
            if (homeScroll.canScrollVertically(delta < 0 ? -1 : 1)) homeScroll.scrollBy(0, delta);
            previewDropPosition();
        }
    });
    private final Runnable ticker = new Runnable() {
        @Override public void run() { if (resumed) { refresh(false); main.postDelayed(this, 30000); } }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        store = new AppStore(this); selection = store.read();
        if (state != null) {
            page = state.getString("page", "home"); selectedDay = state.getInt("day", 6);
            query = state.getString("query", "");
            ArrayList<String> saved = state.getStringArrayList("pending"); if (saved != null) pending.addAll(saved);
        }
        root = column(); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = Math.max(insets.getSystemWindowInsetBottom(), insets.getStableInsetBottom());
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), bottom);
            return insets.consumeSystemWindowInsets();
        });
        root.setOnDragListener((view, event) -> handleAppDrag(event));
        setContentView(root); root.requestApplyInsets(); render();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("page", page); out.putInt("day", selectedDay);
        out.putString("query", query); out.putStringArrayList("pending", new ArrayList<>(pending));
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        if (!UsageRepository.hasAccess(this)) usage = null;
        refresh(true); main.removeCallbacks(ticker); main.postDelayed(ticker, 30000);
    }
    @Override protected void onPause() { dismissGMenu(); cancelAppDrag(); resumed = false; main.removeCallbacks(ticker); super.onPause(); }
    @Override protected void onDestroy() { cancelAppDrag(); generation++; main.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy(); }
    @Override public void onBackPressed() {
        if (dragSession != null) { cancelAppDrag(); return; }
        if (!page.equals("home")) { hideKeyboard(); page = "home"; pending.clear(); render(); }
        else super.onBackPressed();
    }
    private void refresh(boolean loadCatalog) {
        if (isDestroyed()) return;
        final int request = ++generation;
        final List<String> packages = selection.list();
        loading = true;
        if (page.equals("home")) updateHomeTimes();
        worker.execute(() -> {
            List<AppCatalog.Entry> loaded = null;
            String error = null;
            if (loadCatalog) try { loaded = AppCatalog.read(getApplicationContext()); }
            catch (RuntimeException e) { error = "暂时无法读取应用列表，请返回后重试"; }
            if (request != generation) return;
            UsageRepository.Data result = UsageRepository.read(getApplicationContext(), packages);
            final List<AppCatalog.Entry> apps = loaded; final String appError = error;
            main.post(() -> {
                if (isDestroyed() || request != generation) return;
                loading = false; usage = result;
                if (loadCatalog) {
                    catalogLoaded = true; catalogError = appError;
                    if (apps != null) { catalog = apps; entries.clear(); for (AppCatalog.Entry a : apps) entries.put(a.pkg, a); }
                    if (!store.initialized() && apps != null) {
                        if (entries.containsKey("cn.pulse.player")) selection.add("cn.pulse.player");
                        store.write(selection);
                        if (!selection.list().equals(packages)) { render(); refresh(false); return; }
                    }
                }
                if (dragSession != null) { updateHomeTimes(); return; }
                if (page.equals("add")) { if (pickerAdapter != null) pickerAdapter.filter(query); }
                else if (loadCatalog || page.equals("stats")) render();
                else updateHomeTimes();
            });
        });
    }
    private void render() {
        dismissGMenu(); cancelAppDrag(); appGrid = null; homeScroll = null; addButton = null;
        root.removeAllViews(); tileTimes.clear(); totalText = null; totalHint = null; pickerAdapter = null;
        body = column(); root.addView(body, new LinearLayout.LayoutParams(-1, -1));
        if (page.equals("add")) renderPicker(); else if (page.equals("stats")) renderStats(); else renderHome();
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView text(String value, int sp, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color);
        v.setFontFeatureSettings("tnum"); v.setIncludeFontPadding(false); return v;
    }
    private TextView bold(String value, int sp, int color) { TextView v = text(value, sp, color); v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return v; }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private void clickable(View v, int color, int radius, Runnable action) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x164c6657), shape(color, radius), shape(WHITE, radius)));
        v.setOnClickListener(w -> action.run()); v.setFocusable(true);
    }
    private TextView button(String name, boolean primary, Runnable action) {
        TextView v = bold(name, 15, primary ? WHITE : ACCENT); v.setGravity(Gravity.CENTER);
        v.setPadding(dp(16), dp(14), dp(16), dp(14)); v.setMinHeight(dp(48));
        clickable(v, primary ? ACCENT : SOFT, 16, action); return v;
    }
    private void space(LinearLayout parent, int height) { View v = new View(this); parent.addView(v, new LinearLayout.LayoutParams(1, dp(height))); }
    private void line(LinearLayout parent) { View v = new View(this); v.setBackgroundColor(LINE); parent.addView(v, new LinearLayout.LayoutParams(-1, dp(1))); }
    private ScrollView scroll(LinearLayout container, LinearLayout content, boolean stretch) {
        ScrollView v = new ScrollView(this); v.setFillViewport(stretch); v.setClipToPadding(false); v.setVerticalScrollBarEnabled(false);
        v.addView(content, new ScrollView.LayoutParams(-1, -2)); container.addView(v, new LinearLayout.LayoutParams(-1, 0, 1)); return v;
    }
    private void renderHome() {
        LinearLayout header = row(); header.setPadding(dp(26), dp(24), dp(26), dp(12));
        LinearLayout name = column(); name.addView(bold("G space", 28, INK)); space(name, 7);
        name.addView(text(new SimpleDateFormat("M月d日  EEEE", Locale.CHINA).format(new Date()), 13, MUTED));
        header.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView mark = bold("G", 20, WHITE); mark.setGravity(Gravity.CENTER);
        mark.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff), shape(0xff080808, 18), shape(WHITE, 18)));
        mark.setContentDescription("G space菜单"); mark.setFocusable(true);
        mark.setOnClickListener(v -> showGMenu(v));
        header.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));
        body.addView(header);
        LinearLayout content = column(); content.setPadding(dp(22), dp(12), dp(22), dp(12)); homeScroll = scroll(body, content, false);
        LinearLayout summary = column(); summary.setPadding(dp(22), dp(22), dp(22), dp(20));
        LinearLayout caption = row(); caption.addView(text("今日应用使用", 13, MUTED), new LinearLayout.LayoutParams(0, -2, 1));
        caption.addView(text("统计  ›", 13, ACCENT)); summary.addView(caption); space(summary, 16);
        totalText = bold("—", 35, INK); summary.addView(totalText); space(summary, 11);
        totalHint = text("", 12, MUTED); totalHint.setLineSpacing(dp(3), 1); summary.addView(totalHint);
        clickable(summary, WHITE, 22, () -> { selectedDay = 6; page = "stats"; render(); });
        summary.setContentDescription("查看应用使用时长统计"); content.addView(summary); space(content, 28);
        LinearLayout appCaption = row(); appCaption.setPadding(dp(4), 0, dp(4), 0);
        appCaption.addView(bold("我的应用", 15, INK), new LinearLayout.LayoutParams(0, -2, 1));
        appCaption.addView(text(selection.list().isEmpty() ? "" : "长按拖动", 12, MUTED)); content.addView(appCaption); space(content, 14);
        List<String> selected = selection.list();
        if (selected.isEmpty()) {
            LinearLayout empty = column(); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(12), dp(34), dp(12), dp(38));
            empty.addView(bold("留一点空间，给学习", 18, INK)); space(empty, 12);
            TextView hint = text("添加常用的 AI、音乐和学习工具", 13, MUTED); hint.setGravity(Gravity.CENTER); empty.addView(hint); content.addView(empty);
        } else {
            int available = getResources().getConfiguration().screenWidthDp;
            int columns = available < 340 || getResources().getConfiguration().fontScale > 1.3f ? 2 : available >= 600 ? 5 : 3;
            appGrid = new AppGrid(this, columns);
            for (String pkg : selected) appGrid.add(pkg, appTile(pkg));
            content.addView(appGrid, new LinearLayout.LayoutParams(-1, -2));
        }
        if (catalogError != null) content.addView(text(catalogError, 12, MUTED));
        LinearLayout footer = column(); footer.setPadding(dp(26), dp(8), dp(26), dp(18));
        addButton = button("＋  添加应用", false, this::openPicker); footer.addView(addButton); body.addView(footer);
        updateHomeTimes();
    }
    private void dismissGMenu() {
        PopupMenu menu = gMenu; gMenu = null;
        if (menu != null) menu.dismiss();
    }
    private void showGMenu(View anchor) {
        if (dragSession != null) return;
        dismissGMenu();
        PopupMenu menu = new PopupMenu(this, anchor, Gravity.END); gMenu = menu;
        menu.getMenu().add(0, 1, 0, "时长权限");
        menu.getMenu().add(0, 2, 1, "使用说明");
        menu.getMenu().add(0, 3, 2, "关于 G space");
        menu.setOnDismissListener(closed -> { if (gMenu == closed) gMenu = null; });
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: requestAccess(); return true;
                case 2:
                    new AlertDialog.Builder(this).setTitle("使用说明")
                            .setMessage("添加应用：点击首页底部的「添加应用」，搜索并选择。\n\n排序：长按图标直接拖动，松手保存顺序；停在列表边缘可以自动滚动。\n\n移除：把图标拖到下方移除区，确认后只移除入口，手机里的应用和数据都会保留。\n\n时长：开启使用情况访问权限后，点击首页统计卡片查看每天和近 7 天的前台使用时间。后台音乐和网络服务不计入；≈ 表示系统汇总或记录不完整。")
                            .setPositiveButton("知道了", null).show();
                    return true;
                case 3:
                    String version;
                    try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
                    catch (android.content.pm.PackageManager.NameNotFoundException e) { version = "—"; }
                    new AlertDialog.Builder(this).setTitle("G space · " + version)
                            .setMessage("G 系列的学习应用入口。\n\n把常用的应用放在一起，留一点空间给学习。\n\n应用列表和使用记录仅在本机使用。")
                            .setPositiveButton("关闭", null).show();
                    return true;
                default: return false;
            }
        });
        menu.show();
    }
    private View appTile(String pkg) {
        AppCatalog.Entry app = entries.get(pkg);
        LinearLayout tile = column(); tile.setGravity(Gravity.CENTER); tile.setPadding(dp(5), dp(16), dp(5), dp(14));
        ImageView icon = new ImageView(this); icon.setImageDrawable(app == null ? getPackageManager().getDefaultActivityIcon() : app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER); icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48))); space(tile, 11);
        TextView title = text(label(pkg), 13, INK); title.setGravity(Gravity.CENTER); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(title, new LinearLayout.LayoutParams(-1, -2)); space(tile, 6);
        TextView duration = text("—", 11, MUTED); duration.setGravity(Gravity.CENTER); duration.setSingleLine(true);
        tile.addView(duration, new LinearLayout.LayoutParams(-1, -2)); tileTimes.put(pkg, duration);
        clickable(tile, Color.TRANSPARENT, 16, () -> launch(pkg));
        tile.setOnLongClickListener(v -> startAppDrag(pkg, v));
        tile.setContentDescription(label(pkg) + "，点击打开，长按拖动排序，拖到底部可移除入口"); return tile;
    }
    private String label(String pkg) { return entries.containsKey(pkg) ? entries.get(pkg).label : pkg; }
    private void updateHomeTimes() {
        if (totalText == null) return;
        boolean granted = UsageRepository.hasAccess(this);
        if (!granted) { totalText.setText("开启时长统计"); totalText.setTextSize(25); totalHint.setText("点击授权，应用入口仍可直接使用"); }
        else if (usage == null) { totalText.setText(loading ? "读取中…" : "—"); totalText.setTextSize(30); totalHint.setText("已添加应用的前台使用时长"); }
        else if (usage.error != null) { totalText.setText("暂不可用"); totalText.setTextSize(28); totalHint.setText(usage.error); }
        else { totalText.setText(usage.known[6] ? (usage.estimated[6] ? "≈ " : "") + DayWindow.duration(usage.total(6)) : "暂无记录");
            totalText.setTextSize(30); totalHint.setText("仅统计已添加应用 · 点击查看近 7 天"); }
        for (String p : tileTimes.keySet()) {
            String value = !catalogLoaded ? "加载中" : !entries.containsKey(p) ? "不可用 · 拖动移除" : !granted ? "未授权" : usage == null || usage.error != null || !usage.known[6] ? "—" : (usage.estimated[6] ? "≈ " : "") + DayWindow.duration(usage.appTotal(p, 6));
            tileTimes.get(p).setText(value);
        }
    }
    private void launch(String pkg) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) { toast("此应用目前无法打开，可长按拖到底部移除入口"); return; }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(launch);
        } catch (RuntimeException e) { toast("无法打开此应用，请检查它是否已停用或卸载"); }
    }
    private boolean startAppDrag(String pkg, View tile) {
        if (dragSession != null || appGrid == null || !selection.contains(pkg) || !resumed) return false;
        dragSession = new ReorderSession(selection, pkg); dragSource = tile;
        dragInside = false; removeOnEnd = false; removeHovered = false;
        // The root remains attached when tiles change positions or scroll off-screen.
        boolean started;
        try { started = root.startDragAndDrop(null, new View.DragShadowBuilder(tile), dragSession, 0); }
        catch (RuntimeException e) { started = false; }
        if (!started) { finishAppDrag(); toast("暂时无法拖动，请再试一次"); return true; }
        tile.setAlpha(.25f); tile.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        showRemoveTarget(false); return true;
    }
    private boolean handleAppDrag(DragEvent event) {
        if (dragSession == null || event.getLocalState() != dragSession) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED: return true;
            case DragEvent.ACTION_DRAG_ENTERED: return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                locateDrag(event.getX(), event.getY()); return true;
            case DragEvent.ACTION_DRAG_EXITED:
                dragInside = false; dragScroll.stop(); showRemoveTarget(false); return true;
            case DragEvent.ACTION_DROP:
                locateDrag(event.getX(), event.getY()); dragScroll.stop();
                if (removeHovered) { removeOnEnd = true; return true; }
                if (!dragInside || currentDropPosition() < 0) return false;
                if (!dragSession.commit(selection)) return false;
                store.write(selection); return true;
            case DragEvent.ACTION_DRAG_ENDED:
                String removePkg = removeOnEnd && event.getResult() ? dragSession.dragged : null;
                finishAppDrag();
                if (removePkg != null && resumed) main.post(() -> {
                    if (!isDestroyed() && resumed && selection.contains(removePkg)) confirmRemove(removePkg);
                });
                return true;
            default: return true;
        }
    }
    private float[] relativeTo(View view) {
        int[] host = new int[2], child = new int[2];
        root.getLocationOnScreen(host); view.getLocationOnScreen(child);
        return new float[]{dragX + host[0] - child[0], dragY + host[1] - child[1]};
    }
    private boolean pointerInside(View view) {
        if (view == null) return false;
        float[] p = relativeTo(view);
        return p[0] >= 0 && p[0] < view.getWidth() && p[1] >= 0 && p[1] < view.getHeight();
    }
    private void locateDrag(float x, float y) {
        dragX = x; dragY = y;
        showRemoveTarget(pointerInside(addButton));
        dragInside = !removeHovered && pointerInside(homeScroll);
        if (dragInside) { previewDropPosition(); dragScroll.move(relativeTo(homeScroll)[1]); }
        else dragScroll.stop();
    }
    private int currentDropPosition() {
        if (appGrid == null || !dragInside) return -1;
        float[] p = relativeTo(appGrid); return appGrid.target(p[0], p[1]);
    }
    private void previewDropPosition() {
        if (dragSession != null && dragSession.moveTo(currentDropPosition())) appGrid.arrange(dragSession.order(), true);
    }
    private void showRemoveTarget(boolean hovered) {
        removeHovered = hovered;
        if (addButton == null) return;
        addButton.setText(hovered ? "松手移除入口" : "拖到这里移除入口");
        addButton.setTextColor(hovered ? WHITE : 0xff8c4e46);
        addButton.setBackground(shape(hovered ? 0xffa96055 : 0xfff3e8e3, 16));
    }
    private void finishAppDrag() {
        dragScroll.stop();
        if (dragSession != null) dragSession.cancel();
        if (dragSource != null) dragSource.setAlpha(1f);
        dragSession = null; dragSource = null; dragInside = false; removeHovered = false; removeOnEnd = false;
        if (appGrid != null) appGrid.arrange(selection.list(), false);
        if (addButton != null) {
            addButton.setText("＋  添加应用"); addButton.setTextColor(ACCENT);
            clickable(addButton, SOFT, 16, this::openPicker);
        }
    }
    private void cancelAppDrag() {
        if (dragSession == null) return;
        finishAppDrag(); if (root != null) root.cancelDragAndDrop();
    }
    private void confirmRemove(String pkg) {
        new AlertDialog.Builder(this).setTitle("移除「" + label(pkg) + "」入口？")
                .setMessage("手机里的应用和数据都会保留。")
                .setNegativeButton("取消", null).setPositiveButton("移除", (d, w) -> { selection.remove(pkg); saveSelection(); }).show();
    }
    private void saveSelection() { store.write(selection); usage = null; render(); refresh(false); }
    private void openPicker() { page = "add"; query = ""; pending.clear(); render(); if (!catalogLoaded) refresh(true); }
    private void topBar(String title, Runnable back) {
        LinearLayout bar = row(); bar.setPadding(dp(14), dp(10), dp(22), dp(8));
        TextView arrow = text("‹", 34, INK); arrow.setGravity(Gravity.CENTER); arrow.setContentDescription("返回");
        clickable(arrow, Color.TRANSPARENT, 14, back); bar.addView(arrow, new LinearLayout.LayoutParams(dp(48), dp(48)));
        bar.addView(bold(title, 22, INK), new LinearLayout.LayoutParams(0, -2, 1)); body.addView(bar);
    }
    private void renderPicker() {
        topBar("添加应用", this::onBackPressed);
        LinearLayout searchBox = column(); searchBox.setPadding(dp(24), dp(8), dp(24), dp(12));
        EditText search = new EditText(this); search.setTextSize(15); search.setTextColor(INK); search.setHintTextColor(MUTED);
        search.setHint("搜索应用名称"); search.setSingleLine(true); search.setPadding(dp(16), dp(14), dp(16), dp(14));
        search.setBackground(shape(WHITE, 16)); search.setText(query); searchBox.addView(search); body.addView(searchBox);
        android.widget.FrameLayout listFrame = new android.widget.FrameLayout(this);
        ListView list = new ListView(this); list.setDivider(null); list.setCacheColorHint(Color.TRANSPARENT); list.setClipToPadding(false); list.setPadding(dp(12), 0, dp(12), 0);
        TextView empty = text(catalogLoaded ? "没有找到应用" : "正在读取应用…", 14, MUTED); empty.setGravity(Gravity.CENTER);
        listFrame.addView(list, new android.widget.FrameLayout.LayoutParams(-1, -1));
        listFrame.addView(empty, new android.widget.FrameLayout.LayoutParams(-1, -1)); list.setEmptyView(empty);
        pickerAdapter = new PickerAdapter(empty); list.setAdapter(pickerAdapter); pickerAdapter.filter(query);
        list.setOnItemClickListener((parent, view, position, id) -> {
            String pkg = pickerAdapter.visible.get(position).pkg;
            if (selection.contains(pkg)) return;
            if (!pending.remove(pkg)) pending.add(pkg);
            pickerAdapter.notifyDataSetChanged(); updatePickerButton();
        });
        body.addView(listFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout footer = column(); footer.setPadding(dp(24), dp(10), dp(24), dp(18));
        pickerDone = button("完成", true, () -> {
            if (pending.isEmpty()) { onBackPressed(); return; }
            for (String pkg : pending) selection.add(pkg);
            pending.clear(); hideKeyboard(); page = "home"; saveSelection();
        }); footer.addView(pickerDone); body.addView(footer); updatePickerButton();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { query = s.toString(); if (pickerAdapter != null) pickerAdapter.filter(query); }
            public void afterTextChanged(Editable e) {}
        });
        search.clearFocus(); listFrame.setFocusableInTouchMode(true); listFrame.requestFocus();
    }
    private void updatePickerButton() { if (pickerDone != null) pickerDone.setText(pending.isEmpty() ? "完成" : "添加 " + pending.size() + " 个应用"); }
    private final class PickerAdapter extends BaseAdapter {
        final ArrayList<AppCatalog.Entry> visible = new ArrayList<>();
        final TextView empty;
        PickerAdapter(TextView empty) { this.empty = empty; }
        void filter(String query) {
            visible.clear(); String q = query.trim().toLowerCase(Locale.ROOT);
            for (AppCatalog.Entry a : catalog) if (a.label.toLowerCase(Locale.ROOT).contains(q) || a.pkg.toLowerCase(Locale.ROOT).contains(q)) visible.add(a);
            empty.setText(catalogError != null ? catalogError : !catalogLoaded ? "正在读取应用…" : "没有找到应用"); notifyDataSetChanged();
        }
        public int getCount() { return visible.size(); }
        public Object getItem(int i) { return visible.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int position, View recycled, ViewGroup parent) {
            LinearLayout r; ImageView icon; TextView title, subtitle; CheckBox check;
            if (recycled == null) {
                r = row(); r.setPadding(dp(12), dp(12), dp(12), dp(12)); r.setMinimumHeight(dp(72));
                icon = new ImageView(MainActivity.this); r.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
                LinearLayout labels = column(); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(dp(16), 0, dp(10), 0);
                title = text("", 15, INK); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END); labels.addView(title); space(labels, 6);
                subtitle = text("", 11, MUTED); subtitle.setSingleLine(true); subtitle.setEllipsize(TextUtils.TruncateAt.END); labels.addView(subtitle); r.addView(labels, lp);
                check = new CheckBox(MainActivity.this); check.setButtonTintList(ColorStateList.valueOf(ACCENT)); check.setClickable(false); check.setFocusable(false);
                r.addView(check, new LinearLayout.LayoutParams(dp(40), dp(40))); r.setTag(new Object[]{icon, title, subtitle, check});
            } else r = (LinearLayout) recycled;
            Object[] views = (Object[]) r.getTag(); icon = (ImageView) views[0]; title = (TextView) views[1]; subtitle = (TextView) views[2]; check = (CheckBox) views[3];
            AppCatalog.Entry a = visible.get(position); boolean added = selection.contains(a.pkg);
            icon.setImageDrawable(a.icon); title.setText(a.label); subtitle.setText(added ? "已添加" : a.pkg);
            check.setChecked(added || pending.contains(a.pkg)); r.setAlpha(added ? .5f : 1f);
            r.setContentDescription(a.label + (added ? "，已添加" : pending.contains(a.pkg) ? "，已选中" : "，未选中")); return r;
        }
    }
    private void renderStats() {
        topBar("使用时长", this::onBackPressed);
        LinearLayout content = column(); content.setPadding(dp(24), dp(10), dp(24), dp(24)); scroll(body, content, false);
        if (!UsageRepository.hasAccess(this) || usage != null && !usage.granted) {
            space(content, 30); content.addView(bold("让时间有迹可循", 24, INK)); space(content, 16);
            TextView explain = text("授权后，可查看已添加应用的前台使用时长。记录只保存在本机。", 15, MUTED); explain.setLineSpacing(dp(7), 1); content.addView(explain); space(content, 24);
            content.addView(button("开启使用情况访问权限", true, this::requestAccess)); space(content, 16);
            content.addView(text("在系统页面找到「G space」，开启后返回即可。", 13, MUTED)); return;
        }
        if (usage == null || usage.error != null) {
            space(content, 36); content.addView(text(usage == null ? "正在读取使用记录…" : usage.error, 16, MUTED)); space(content, 20);
            content.addView(button("刷新", false, () -> refresh(false))); return;
        }
        LinearLayout tabs = row();
        TextView today = button("今天", selectedDay == 6, () -> { selectedDay = 6; render(); });
        TextView week = button("近 7 天", selectedDay == -1, () -> { selectedDay = -1; render(); });
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, -2, 1); tabLp.setMargins(0, 0, dp(8), 0);
        tabs.addView(today, tabLp); tabs.addView(week, new LinearLayout.LayoutParams(0, -2, 1)); content.addView(tabs); space(content, 26);
        boolean known = selectedDay < 0 ? usage.anyKnown() : usage.known[selectedDay];
        boolean approximate = selectedDay < 0 ? usage.weekApproximate() : usage.estimated[selectedDay];
        long value = selectedDay < 0 ? usage.totalWeek() : usage.total(selectedDay);
        content.addView(text(selectedDay == -1 ? "近 7 天 · 当前添加的应用" : date(usage.days[selectedDay], "M月d日") + " · 当前添加的应用", 13, MUTED)); space(content, 12);
        content.addView(bold(known ? (approximate ? "≈ " : "") + DayWindow.duration(value) : "暂无记录", 32, INK)); space(content, 22);
        Chart chart = new Chart(); content.addView(chart, new LinearLayout.LayoutParams(-1, dp(130)));
        LinearLayout dates = row();
        for (int i = 0; i < 7; i++) {
            final int day = i;
            TextView d = text(i == 6 ? "今天" : date(usage.days[i], "M/d"), 11, selectedDay == i ? ACCENT : MUTED);
            d.setGravity(Gravity.CENTER); d.setMinHeight(dp(44));
            clickable(d, selectedDay == i ? SOFT : Color.TRANSPARENT, 10, () -> { selectedDay = day; render(); });
            d.setContentDescription(date(usage.days[i], "M月d日") + "，" + (usage.known[i] ? DayWindow.duration(usage.total(i)) : "暂无记录"));
            dates.addView(d, new LinearLayout.LayoutParams(0, -2, 1));
        }
        content.addView(dates); space(content, 24); line(content); space(content, 16);
        ArrayList<String> sorted = new ArrayList<>(selection.list()); sorted.sort((a, b) -> Long.compare(usage.appTotal(b, selectedDay), usage.appTotal(a, selectedDay)));
        if (sorted.isEmpty()) { content.addView(text("添加应用后，在这里查看它们的使用时长。", 14, MUTED)); space(content, 16); content.addView(button("添加应用", false, this::openPicker)); }
        for (String pkg : sorted) {
            LinearLayout item = row(); item.setPadding(0, dp(12), 0, dp(12));
            ImageView icon = new ImageView(this); icon.setImageDrawable(entries.containsKey(pkg) ? entries.get(pkg).icon : getPackageManager().getDefaultActivityIcon());
            item.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
            TextView name = text(label(pkg), 14, INK); name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(dp(12), 0, dp(10), 0); item.addView(name, lp);
            TextView amount = text(known ? (approximate ? "≈ " : "") + DayWindow.duration(usage.appTotal(pkg, selectedDay)) : "—", 12, MUTED); item.addView(amount); content.addView(item);
        }
        space(content, 24);
        TextView note = text("统计前台使用，不包含后台音乐或网络服务；从其他入口打开也会计入。\n\n≈ 表示使用系统按日汇总或记录不完整，可能存在日界线差异。缺少记录的日期显示 —，不计入合计。系统数据可能稍有延迟。", 12, MUTED);
        note.setLineSpacing(dp(5), 1); content.addView(note); space(content, 18);
        content.addView(button("刷新记录", false, () -> refresh(false)));
    }
    private final class Chart extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Chart() { super(MainActivity.this); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); float cell = getWidth() / 7f, bottom = getHeight() - dp(9), top = dp(22);
            long maximum = 60000; for (int i = 0; i < 7; i++) maximum = Math.max(maximum, usage.total(i));
            for (int i = 0; i < 7; i++) {
                float center = cell * (i + .5f), width = Math.min(dp(25), cell * .52f);
                paint.setColor(LINE); c.drawRoundRect(center - width / 2, top, center + width / 2, bottom, dp(5), dp(5), paint);
                if (usage.known[i]) {
                    float height = usage.total(i) == 0 ? dp(3) : Math.max(dp(5), (bottom - top) * usage.total(i) / maximum);
                    paint.setColor(selectedDay == i || selectedDay == -1 ? ACCENT : 0xffb6c5b7);
                    c.drawRoundRect(center - width / 2, bottom - height, center + width / 2, bottom, dp(5), dp(5), paint);
                } else { paint.setColor(MUTED); paint.setTextSize(dp(13)); paint.setTextAlign(Paint.Align.CENTER); c.drawText("—", center, bottom - dp(8), paint); }
            }
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) { selectedDay = Math.max(0, Math.min(6, (int) (event.getX() / (getWidth() / 7f)))); performClick(); render(); }
            return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }
    private String date(long at, String pattern) { return new SimpleDateFormat(pattern, Locale.CHINA).format(new Date(at)); }
    private void requestAccess() {
        try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
        catch (RuntimeException e) { try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); } catch (RuntimeException e2) { toast("请在系统设置中搜索「使用情况访问权限」"); } }
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private void hideKeyboard() { View v = getCurrentFocus(); if (v != null) ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0); }
}
