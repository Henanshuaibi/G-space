package cn.g.space;

import java.util.Calendar;
import java.util.TimeZone;

public final class DayWindow {
    private DayWindow() {}
    /** Seven local calendar dates, ending today. Eight midnight boundaries. */
    public static long[] lastSeven(long now, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DATE, -6);
        long[] days = new long[8];
        for (int i = 0; i < days.length; i++) { days[i] = c.getTimeInMillis(); c.add(Calendar.DATE, 1); }
        return days;
    }
    public static String duration(long ms) {
        if (ms <= 0) return "0 分钟";
        if (ms < 60000) return "不足 1 分钟";
        long minutes = ms / 60000;
        return minutes < 60 ? minutes + " 分钟" : minutes / 60 + " 小时" + (minutes % 60 == 0 ? "" : " " + minutes % 60 + " 分");
    }
}
