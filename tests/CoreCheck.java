package cn.g.space;

import java.time.ZonedDateTime;
import java.util.*;

public final class CoreCheck {
    static int checks;
    static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    static void equal(long actual, long expected, String message) { check(actual == expected, message + ": " + actual + " != " + expected); }
    static UsageTimeline timeline(long end) { return new UsageTimeline(new long[]{100, 200, 300, 400}, end, Arrays.asList("app.a", "app.b")); }
    static void event(UsageTimeline t, long at, int type, String pkg, String cls) { t.accept(at, type, pkg, cls); }
    public static void main(String[] args) {
        AppSelection s = new AppSelection(Arrays.asList("app.a", "app.b", "app.a", "", "bad/path", "app.c"));
        check(s.list().equals(Arrays.asList("app.a", "app.b", "app.c")), "unique valid packages, preserve order");
        check(s.move("app.c", 0), "move existing entry");
        check(s.list().equals(Arrays.asList("app.c", "app.a", "app.b")), "new order");
        check(!s.move("missing.app", 0) && !s.move("app.c", -1) && !s.move("app.c", 9), "reject invalid reorders");
        check(s.remove("app.a") && !s.remove("app.a"), "idempotent removal");
        List<String> detached = s.list(); detached.clear(); equal(s.list().size(), 2, "copies do not mutate selection");
        check(s.add("app.a"), "re-add removed package");
        UsageTimeline t = timeline(380);
        event(t, 80, 1, "app.a", "Main"); event(t, 230, 2, "app.a", "Main");
        long[] n = t.finish().get("app.a");
        check(Arrays.equals(n, new long[]{100, 30, 0}), "clip and split a session across local midnights");
        t = timeline(380); event(t, 120, 1, "app.a", "Main"); event(t, 130, 1, "app.a", "Main");
        event(t, 140, 1, "app.a", "Detail"); event(t, 150, 2, "app.a", "Main");
        event(t, 170, 2, "app.a", "Detail"); event(t, 180, 2, "app.a", "Detail");
        equal(t.finish().get("app.a")[0], 50, "overlapping activities counted once; duplicate events ignored");
        t = timeline(380); event(t, 110, 1, "app.a", "Main"); event(t, 150, 16, null, null);
        event(t, 160, 17, null, null); event(t, 170, 2, "app.a", "Main");
        equal(t.finish().get("app.a")[0], 40, "screen off stops foreground time");
        t = timeline(380); event(t, 110, 1, "app.a", "Main"); event(t, 140, 17, null, null);
        equal(t.finish().get("app.a")[0], 30, "lock screen closes sessions");
        t = timeline(380); event(t, 110, 1, "app.a", "Main"); event(t, 140, 26, null, null);
        event(t, 150, 1, "app.a", "Main"); event(t, 170, 27, null, null);
        event(t, 180, 1, "app.a", "Main"); event(t, 190, 2, "app.a", "Main");
        equal(t.finish().get("app.a")[0], 40, "shutdown closes; unknown shutdown-to-startup sessions ignored");
        t = timeline(380); event(t, 110, 1, "app.a", "Main"); event(t, 170, 27, null, null);
        equal(t.finish().get("app.a")[0], 0, "orphan resume before startup must not include reboot time");
        t = timeline(380); event(t, 110, 2, "app.a", "Main"); event(t, 130, 1, "unselected.app", "Main");
        event(t, 320, 1, "app.b", "Main"); event(t, 390, 2, "app.b", "Main");
        Map<String, long[]> result = t.finish();
        equal(result.get("app.b")[2], 60, "ongoing session clipped at query end");
        equal(result.get("app.a")[0], 0, "unmatched pause does not invent a session");
        check(!result.containsKey("unselected.app"), "only selected apps counted");
        equal(t.finish().get("app.b")[2], 60, "finishing twice does not double count");
        t = timeline(380); event(t, 120, 1, "app.a", "Main"); event(t, 160, 23, "app.a", "Main");
        equal(t.finish().get("app.a")[0], 40, "activity stop fallback");
        t = timeline(380); event(t, 120, 1, "app.a", null); event(t, 160, 2, "app.a", null);
        equal(t.finish().get("app.a")[0], 40, "legacy events without activity class");
        long now = ZonedDateTime.parse("2026-10-16T23:59:00+08:00[Asia/Shanghai]").toInstant().toEpochMilli();
        long[] days = DayWindow.lastSeven(now, TimeZone.getTimeZone("Asia/Shanghai"));
        equal(days[6], ZonedDateTime.parse("2026-10-16T00:00:00+08:00[Asia/Shanghai]").toInstant().toEpochMilli(), "today begins at local midnight");
        check(days[0] < days[6] && days[7] > now, "seven date windows");
        long spring = ZonedDateTime.parse("2026-03-08T20:00:00-04:00[America/New_York]").toInstant().toEpochMilli();
        long[] dst = DayWindow.lastSeven(spring, TimeZone.getTimeZone("America/New_York"));
        equal(dst[7] - dst[6], 23 * 3600000L, "DST day is 23 hours, not fixed 24");
        long fall = ZonedDateTime.parse("2026-11-01T20:00:00-05:00[America/New_York]").toInstant().toEpochMilli();
        dst = DayWindow.lastSeven(fall, TimeZone.getTimeZone("America/New_York"));
        equal(dst[7] - dst[6], 25 * 3600000L, "DST fall day is 25 hours");
        check(DayWindow.duration(0).equals("0 分钟"), "zero formatting");
        check(DayWindow.duration(59000).equals("不足 1 分钟"), "don't round short sessions to zero");
        check(DayWindow.duration(3660000).equals("1 小时 1 分"), "hour formatting");
        equal(UsageRepository.bucketDay(days[0] - 1, days), -1, "ignore expanded query interval before requested dates");
        equal(UsageRepository.bucketDay(days[7], days), -1, "exclusive end day");
        equal(UsageRepository.bucketDay(days[6], days), 6, "aggregate assigned to its actual bucket date");
        System.out.println("PASS: " + checks + " selection, foreground, midnight, screen-lock, reboot, DST and bucket checks");
    }
}
