package com.zikra.tv;

public final class PrayerAlarmPolicyTest {
    private static int count;private static void check(boolean value,String text){count++;if(!value)throw new AssertionError(text);}
    public static void main(String[] args){
        long epoch=1792897500000L;
        check(PrayerAlarmPolicy.prayer("Fajr"),"Fajr is an adhan prayer");check(!PrayerAlarmPolicy.prayer("Sunrise"),"Sunrise must not call adhan");check(!PrayerAlarmPolicy.prayer(null),"Reject missing key");
        check(PrayerAlarmPolicy.deliver(epoch,epoch,0),"Deliver on time");check(PrayerAlarmPolicy.deliver(epoch,epoch+120000,0),"Short system delay may deliver");check(!PrayerAlarmPolicy.deliver(epoch,epoch+300000,0),"Never play a stale prayer");check(!PrayerAlarmPolicy.deliver(epoch,epoch-60000,0),"Never call a minute early");check(!PrayerAlarmPolicy.deliver(epoch,epoch,epoch),"Do not duplicate already delivered alarm");check(!PrayerAlarmPolicy.deliver(0,epoch,0),"Reject missing timestamp");
        check(PrayerAlarmPolicy.next(new long[]{epoch+2000,epoch-1000,epoch+1000},epoch)==epoch+1000,"Choose next future prayer independent of input order");check(PrayerAlarmPolicy.next(new long[]{epoch-1000},epoch)==0,"No invented next-day prayer");
        check(PrayerAlarmPolicy.epoch("2026-10-24","06:03","Europe/Berlin")==1792814580000L,"Before DST change");
        check(PrayerAlarmPolicy.epoch("2026-10-25","05:05","Europe/Berlin")==1792901100000L,"After DST change");
        check(PrayerAlarmPolicy.epoch("2026-10-25","2026-10-25T05:05:00+01:00","UTC")==1792901100000L,"ISO time offset is applied once");
        check(PrayerAlarmPolicy.epoch("2026-10-25","2026-10-25T04:05:00Z","Europe/Berlin")==1792901100000L,"Zulu time is not offset again");
        check(PrayerAlarmPolicy.epoch("2026-10-25","2026-10-25T01:05:00-03:00","Europe/Berlin")==1792901100000L,"Negative offset is applied correctly");
        System.out.println("PrayerAlarmPolicy: "+count+" checks passed");
    }
}
