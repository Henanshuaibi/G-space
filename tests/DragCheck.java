package cn.g.space;

import java.util.*;

public final class DragCheck {
    static int checks;
    static void check(boolean ok, String reason) { checks++; if (!ok) throw new AssertionError(reason); }
    static final class Host implements DragAutoScroll.Host {
        long now, due; Runnable pending; boolean valid = true; int offset, frames;
        public void post(Runnable task, int delay) { check(pending == null, "one scroll callback at a time"); pending = task; due = now + delay; }
        public void remove(Runnable task) { if (pending == task) pending = null; }
        public boolean valid() { return valid; }
        public int height() { return 400; }
        public int edge() { return 64; }
        public int maxStep() { return 15; }
        public void scrollBy(int delta) { offset = Math.max(0, Math.min(4000, offset + delta)); frames++; }
        void advance(long ms) {
            long until = now + ms;
            while (pending != null && due <= until) { now = due; Runnable r = pending; pending = null; r.run(); }
            now = until;
        }
    }
    public static void main(String[] args) {
        List<String> original = Arrays.asList("app.a", "app.b", "app.c", "app.d", "app.e");
        AppSelection saved = new AppSelection(original);
        ReorderSession drag = new ReorderSession(saved, "app.a");
        check(drag.moveTo(4), "first app moves to final slot");
        check(drag.order().equals(Arrays.asList("app.b", "app.c", "app.d", "app.e", "app.a")), "other apps keep their relative order");
        check(saved.list().equals(original), "hover preview never mutates saved selection");
        check(!drag.moveTo(4), "stationary pointer does not repeatedly rearrange");
        check(drag.moveTo(1), "reverse direction within same drag");
        check(drag.commit(saved), "valid drop commits once");
        check(saved.list().equals(Arrays.asList("app.b", "app.a", "app.c", "app.d", "app.e")), "drop commits final preview");
        check(!drag.commit(saved) && !drag.moveTo(0), "ended drag cannot mutate again");
        List<String> committed = saved.list();
        drag = new ReorderSession(saved, "app.e"); drag.moveTo(0); drag.cancel();
        check(!drag.commit(saved) && saved.list().equals(committed), "cancel/outside drop preserves pre-drag order");
        drag = new ReorderSession(saved, "app.e"); drag.moveTo(0); saved.remove("app.c");
        List<String> concurrent = saved.list();
        check(!drag.commit(saved) && saved.list().equals(concurrent), "stale snapshot cannot replace concurrent changes");
        drag = new ReorderSession(saved, "app.e"); check(drag.commit(saved), "drop at original cell is valid and unchanged");
        AppSelection one = new AppSelection(Arrays.asList("app.a"));
        drag = new ReorderSession(one, "app.a"); check(!drag.moveTo(1) && drag.commit(one), "single-app drag");
        check(GridDropTarget.slot(5, 5, 300, 100, 3, 5) == 0, "top left cell");
        check(GridDropTarget.slot(250, 40, 300, 100, 3, 5) == 2, "top right cell");
        check(GridDropTarget.slot(50, 150, 300, 100, 3, 5) == 3, "second row first cell");
        check(GridDropTarget.slot(250, 150, 300, 100, 3, 5) == 4, "empty last-row cell drops at end");
        check(GridDropTarget.slot(250, 900, 300, 100, 3, 5) == 4, "blank content below grid drops at end");
        check(GridDropTarget.slot(50, -1, 300, 100, 3, 5) == -1, "header is not a reorder target");
        check(GridDropTarget.slot(-1, 50, 300, 100, 3, 5) == -1 && GridDropTarget.slot(300, 50, 300, 100, 3, 5) == -1, "outside grid width rejected");
        check(GridDropTarget.slot(250, 150, 300, 100, 2, 5) == 3, "narrow two-column layout");
        check(GridDropTarget.slot(499, 1, 500, 100, 5, 10) == 4, "wide five-column layout");
        check(GridDropTarget.slot(2, 2, 300, 100, 3, 0) == -1 && GridDropTarget.slot(2, 2, 0, 0, 0, 1) == -1, "empty/unmeasured grid cannot accept drops");
        for (int columns : new int[]{2, 3, 5}) for (int count = 1; count <= 40; count++) {
            int rows = (count + columns - 1) / columns;
            for (int i = 0; i < count; i++) check(GridDropTarget.slot((i % columns + .5f) * 500 / columns, (i / columns + .5f) * 100, 500, 100, columns, count) == i, "every cell center hits its own slot");
            check(GridDropTarget.slot(499, rows * 100 + 20, 500, 100, columns, count) == count - 1, "bottom padding reaches last app");
        }
        Host h = new Host(); DragAutoScroll scrolling = new DragAutoScroll(h);
        for (int i = 0; i < 250; i++) { scrolling.move(395 + i % 3); h.advance(8); }
        check(h.frames >= 60 && h.offset > 700, "frequent move events never postpone scroll frames");
        scrolling.move(399); h.advance(20000); check(h.offset == 4000, "stationary bottom edge keeps scrolling to last app");
        scrolling.move(1); h.advance(20000); check(h.offset == 0, "stationary top edge keeps scrolling to first app");
        scrolling.move(399); h.advance(400); int down = h.offset;
        scrolling.move(1); h.advance(200); check(h.offset < down, "edge direction reversal");
        scrolling.move(200); int middle = h.offset; h.advance(1000); check(h.offset == middle, "center stops scrolling");
        scrolling.move(399); h.advance(64); scrolling.stop(); int stopped = h.offset;
        h.advance(1000); check(h.pending == null && h.offset == stopped, "drop/cancel removes pending callback");
        scrolling.move(399); h.advance(64); check(h.offset > stopped, "next drag can restart scrolling");
        h.valid = false; h.advance(64); check(h.pending == null, "paused/exited/removed drag stops callback");
        System.out.println("PASS: " + checks + " drag preview, commit, cancellation, grid hit targets and continuous edge-scroll checks");
    }
}
