package com.zikra.tv;

/** Shared, Android-independent rules for delivery and timetable timestamps. */
public final class PrayerAlarmPolicy {
    private PrayerAlarmPolicy() {}
    public static boolean allowed(boolean background,boolean visible){return background||visible;}
    public static boolean prayer(String key) { return "Fajr".equals(key)||"Dhuhr".equals(key)||"Asr".equals(key)||"Maghrib".equals(key)||"Isha".equals(key); }
    public static boolean deliver(long epoch,long now,long played) { return epoch>0&&epoch!=played&&now-epoch>=-2000&&now-epoch<300000; }
    public static long next(long[] epochs,long now) { long result=0;for(long epoch:epochs)if(epoch>now&&(result==0||epoch<result))result=epoch;return result; }
    public static long epoch(String day,String time,String zone) {
        java.util.regex.Matcher iso=java.util.regex.Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})T(\\d{2}:\\d{2})(?::(\\d{2}))?(Z|[+-]\\d{2}:\\d{2})$").matcher(time);
        int offset=0,seconds=0;
        if(iso.matches()){day=iso.group(1);time=iso.group(2);seconds=iso.group(3)==null?0:Integer.parseInt(iso.group(3));String z=iso.group(4);if(!"Z".equals(z))offset=(Integer.parseInt(z.substring(1,3))*60+Integer.parseInt(z.substring(4)))*(z.charAt(0)=='-'?-1:1);zone="UTC";}
        java.util.Calendar c=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(zone));c.clear();c.setLenient(false);String[] d=day.split("-"),t=time.split(" ")[0].split(":");c.set(Integer.parseInt(d[0]),Integer.parseInt(d[1])-1,Integer.parseInt(d[2]),Integer.parseInt(t[0]),Integer.parseInt(t[1]),seconds);return c.getTimeInMillis()-offset*60000L;
    }
}
