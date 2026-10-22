package cn.g.space;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;

/** Keeps views attached throughout the drag, including the source's placeholder. */
public final class AppGrid extends ViewGroup {
    private final int columns, gap;
    private final ArrayList<View> order = new ArrayList<>();
    private int rowHeight;
    private boolean animateNext;
    public AppGrid(Context context, int columns) {
        super(context); this.columns = columns;
        gap = Math.round(3 * getResources().getDisplayMetrics().density);
        setClipChildren(false); setClipToPadding(false);
    }
    public void add(String pkg, View tile) {
        tile.setTag(pkg); tile.setId(View.generateViewId()); order.add(tile);
        addView(tile, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }
    public void arrange(List<String> packages, boolean animate) {
        ArrayList<View> next = new ArrayList<>();
        for (String pkg : packages) for (View tile : order) if (pkg.equals(tile.getTag())) { next.add(tile); break; }
        if (next.size() != order.size() || next.equals(order)) return;
        order.clear(); order.addAll(next); animateNext = animate; requestLayout();
    }
    public int target(float x, float y) { return GridDropTarget.slot(x, y, getWidth(), rowHeight, columns, order.size()); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), cellWidth = width / columns;
        rowHeight = 0;
        for (View tile : order) {
            tile.measure(MeasureSpec.makeMeasureSpec(Math.max(0, cellWidth - gap * 2), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            rowHeight = Math.max(rowHeight, tile.getMeasuredHeight() + gap * 2);
        }
        setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(((order.size() + columns - 1) / columns) * rowHeight, heightSpec));
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        for (int i = 0; i < order.size(); i++) {
            View tile = order.get(i);
            float oldX = tile.getX(), oldY = tile.getY(); boolean hadLayout = tile.isLaidOut();
            tile.animate().cancel();
            int x = (i % columns) * width / columns + gap, y = (i / columns) * rowHeight + gap;
            tile.layout(x, y, x + tile.getMeasuredWidth(), y + tile.getMeasuredHeight());
            tile.setAccessibilityTraversalAfter(i == 0 ? View.NO_ID : order.get(i - 1).getId());
            if (animateNext && hadLayout) {
                tile.setTranslationX(oldX - x); tile.setTranslationY(oldY - y);
                tile.animate().translationX(0).translationY(0).setDuration(140).start();
            } else { tile.setTranslationX(0); tile.setTranslationY(0); }
        }
        animateNext = false;
    }
}
