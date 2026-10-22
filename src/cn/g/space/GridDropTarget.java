package cn.g.space;

/** Grid cell under the finger, including the unused cells in the last row. */
public final class GridDropTarget {
    private GridDropTarget() {}
    public static int slot(float x, float y, int width, int rowHeight, int columns, int count) {
        if (width <= 0 || rowHeight <= 0 || columns <= 0 || count <= 0 || x < 0 || x >= width || y < 0) return -1;
        int row = Math.min((count - 1) / columns, (int) (y / rowHeight));
        int col = Math.min(columns - 1, (int) (x * columns / width));
        return Math.min(count - 1, row * columns + col);
    }
    public static int scroll(float y, int height, int edge, int maxStep) {
        if (height <= 0 || edge <= 0 || maxStep <= 0) return 0;
        int band = Math.min(edge, height / 2);
        if (band == 0) return 0;
        if (y < band) return -Math.max(1, Math.round(maxStep * Math.min(1f, (band - y) / band)));
        if (y > height - band) return Math.max(1, Math.round(maxStep * Math.min(1f, (y - height + band) / band)));
        return 0;
    }
}
