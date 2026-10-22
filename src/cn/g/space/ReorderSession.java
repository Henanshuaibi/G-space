package cn.g.space;

import java.util.List;

/** Live preview is isolated from saved selection until a valid drop. */
public final class ReorderSession {
    public final String dragged;
    private final List<String> original;
    private final AppSelection preview;
    private boolean ended;
    public ReorderSession(AppSelection current, String pkg) {
        if (!current.contains(pkg)) throw new IllegalArgumentException("Missing app");
        dragged = pkg; original = current.list(); preview = new AppSelection(original);
    }
    public List<String> order() { return preview.list(); }
    public boolean moveTo(int position) { return !ended && preview.move(dragged, position); }
    public boolean commit(AppSelection current) {
        if (ended || !original.equals(current.list())) { cancel(); return false; }
        ended = true;
        int destination = preview.indexOf(dragged);
        if (current.indexOf(dragged) != destination) current.move(dragged, destination);
        return true;
    }
    public void cancel() { ended = true; }
}
