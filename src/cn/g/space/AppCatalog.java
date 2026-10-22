package cn.g.space;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AppCatalog {
    public static final class Entry {
        public final String pkg, label;
        public final Drawable icon;
        Entry(String pkg, String label, Drawable icon) { this.pkg = pkg; this.label = label; this.icon = icon; }
    }
    public static List<Entry> read(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, Entry> found = new HashMap<>();
        for (ResolveInfo r : pm.queryIntentActivities(intent, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(context.getPackageName()) || found.containsKey(pkg) || !r.activityInfo.enabled || !r.activityInfo.exported) continue;
            try { found.put(pkg, new Entry(pkg, pm.getApplicationLabel(r.activityInfo.applicationInfo).toString(), pm.getApplicationIcon(r.activityInfo.applicationInfo))); }
            catch (RuntimeException ignored) {}
        }
        ArrayList<Entry> result = new ArrayList<>(found.values());
        Collator collator = Collator.getInstance(Locale.CHINA);
        result.sort((a, b) -> {
            boolean ag = a.pkg.equals("cn.pulse.player"), bg = b.pkg.equals("cn.pulse.player");
            return ag != bg ? (ag ? -1 : 1) : collator.compare(a.label, b.label);
        });
        return result;
    }
}
