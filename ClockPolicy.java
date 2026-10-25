package com.zikra.tv;

/** Accept only a fresh HTTPS time response, never a cached asset's timestamp. */
public final class ClockPolicy {
    private ClockPolicy() {}
    public static long sample(long date, String age, long started, long finished) {
        long elapsed = finished-started;
        if (date < 1577836800000L || elapsed < 0 || elapsed > 5000) return 0;
        long cached = 0;
        try { if (age != null) cached = Long.parseLong(age.trim()); } catch (NumberFormatException error) { return 0; }
        if (cached < 0 || cached > 5) return 0;
        return date + cached*1000 + elapsed/2;
    }
}
