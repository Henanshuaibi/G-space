package cn.g.space;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public final class UsageRepository {
    public static final class Data {
        public final long[] days;
        public final Map<String, long[]> apps = new HashMap<>();
        public final boolean[] known = new boolean[7], estimated = new boolean[7];
        public boolean granted;
        public String error;
        Data(long now, Collection<String> packages) {
            days = DayWindow.lastSeven(now, TimeZone.getDefault());
            for (String p : packages) apps.put(p, new long[7]);
        }
        public long total(int day) { long total = 0; for (long[] a : apps.values()) total += a[day]; return total; }
        public long appTotal(String pkg, int day) {
            long[] values = apps.get(pkg); if (values == null) return 0;
            if (day >= 0) return values[day];
            long total = 0; for (long n : values) total += n; return total;
        }
        public long totalWeek() { long total = 0; for (int i = 0; i < 7; i++) total += total(i); return total; }
        public boolean anyKnown() { for (boolean v : known) if (v) return true; return false; }
        public boolean weekApproximate() {
            for (int i = 0; i < 7; i++) if (!known[i] || estimated[i]) return true;
            return false;
        }
    }
    public static boolean hasAccess(Context context) {
        try {
            AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException e) { return false; }
    }
    public static Data read(Context context, Collection<String> packages) {
        long now = System.currentTimeMillis();
        Data out = new Data(now, packages);
        out.granted = hasAccess(context);
        if (!out.granted) return out;
        try {
            UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (manager == null) { out.error = "这台设备暂不提供使用记录"; return out; }
            UsageTimeline timeline = new UsageTimeline(out.days, now, packages);
            // Warm up with yesterday's events to preserve sessions that cross midnight.
            UsageEvents events = manager.queryEvents(out.days[0] - 86400000L, now);
            long earliest = Long.MAX_VALUE;
            if (events != null) {
                UsageEvents.Event event = new UsageEvents.Event();
                while (events.hasNextEvent()) {
                    events.getNextEvent(event);
                    earliest = Math.min(earliest, event.getTimeStamp());
                    timeline.accept(event.getTimeStamp(), event.getEventType(), event.getPackageName(), event.getClassName());
                }
            }
            Map<String, long[]> exact = timeline.finish();
            // Android only retains detailed events for a few days. Older dates use
            // its daily buckets, explicitly marked approximate instead of inventing zeros.
            Map<String, long[]> daily = new HashMap<>();
            for (String p : packages) daily.put(p, new long[7]);
            boolean[] bucketExists = new boolean[7];
            List<UsageStats> stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, out.days[0], now);
            Map<String, Long> deduplicated = new HashMap<>();
            if (stats != null) for (UsageStats stat : stats) {
                long first = stat.getFirstTimeStamp();
                int day = bucketDay(first, out.days);
                if (day < 0) continue; // queryUsageStats may expand beyond the requested interval.
                bucketExists[day] = true;
                if (!daily.containsKey(stat.getPackageName())) continue;
                String key = stat.getPackageName() + "\n" + first;
                long value = Math.max(0, Math.min(stat.getTotalTimeInForeground(), out.days[day + 1] - out.days[day]));
                long previous = deduplicated.containsKey(key) ? deduplicated.get(key) : 0;
                if (value > previous) { daily.get(stat.getPackageName())[day] += value - previous; deduplicated.put(key, value); }
            }
            for (int i = 0; i < 7; i++) {
                boolean completeEvents = earliest <= out.days[i];
                boolean partialEvents = earliest < Math.min(now, out.days[i + 1]);
                out.known[i] = completeEvents || bucketExists[i] || partialEvents;
                out.estimated[i] = out.known[i] && !completeEvents;
                for (String p : packages) {
                    long value = completeEvents ? exact.get(p)[i] : bucketExists[i] ? daily.get(p)[i] : exact.get(p)[i];
                    out.apps.get(p)[i] = Math.min(value, Math.min(now, out.days[i + 1]) - out.days[i]);
                }
            }
            if (!hasAccess(context)) { out.granted = false; out.apps.clear(); }
        } catch (SecurityException e) { out.granted = false; }
        catch (RuntimeException e) { out.error = "暂时无法读取使用记录，请稍后刷新"; }
        return out;
    }
    static int bucketDay(long first, long[] days) {
        for (int i = 0; i < days.length - 1; i++) if (first >= days[i] && first < days[i + 1]) return i;
        return -1;
    }
}
