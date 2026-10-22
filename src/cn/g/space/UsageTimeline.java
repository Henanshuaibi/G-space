package cn.g.space;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Consumes ordered Android foreground events; counts the union of activities per package. */
public final class UsageTimeline {
    public static final int RESUME = 1, PAUSE = 2, SCREEN_OFF = 16, LOCK = 17,
            STOP = 23, SHUTDOWN = 26, STARTUP = 27;
    private static final class Active {
        final HashSet<String> activities = new HashSet<>();
        long since;
    }
    private final long[] days;
    private final long end;
    private final Map<String, long[]> totals = new HashMap<>();
    private final Map<String, Active> active = new HashMap<>();
    private boolean shutdown;
    private long last = Long.MIN_VALUE;
    public UsageTimeline(long[] boundaries, long now, Collection<String> packages) {
        days = boundaries.clone(); end = now;
        for (String p : packages) totals.put(p, new long[days.length - 1]);
    }
    public void accept(long at, int kind, String pkg, String activity) {
        if (at > end || at < last) return;
        last = at;
        if (kind == STARTUP) { active.clear(); shutdown = false; return; }
        if (kind == SHUTDOWN) { closeAll(at); shutdown = true; return; }
        if (kind == SCREEN_OFF || kind == LOCK) { closeAll(at); return; }
        if (shutdown || !totals.containsKey(pkg)) return;
        String key = activity == null ? "" : activity;
        Active a = active.get(pkg);
        if (kind == RESUME) {
            if (a == null) { a = new Active(); a.since = at; active.put(pkg, a); }
            a.activities.add(key);
        } else if ((kind == PAUSE || kind == STOP) && a != null) {
            if (a.activities.remove(key) && a.activities.isEmpty()) {
                add(pkg, a.since, at); active.remove(pkg);
            }
        }
    }
    private void closeAll(long at) {
        for (String p : new ArrayList<>(active.keySet())) add(p, active.get(p).since, at);
        active.clear();
    }
    private void add(String p, long from, long to) {
        long[] values = totals.get(p);
        for (int i = 0; i < values.length; i++) {
            long length = Math.min(Math.min(to, end), days[i + 1]) - Math.max(from, days[i]);
            if (length > 0) values[i] += length;
        }
    }
    public Map<String, long[]> finish() {
        closeAll(end);
        Map<String, long[]> result = new HashMap<>();
        for (String p : totals.keySet()) result.put(p, totals.get(p).clone());
        return result;
    }
}
