package com.zikra.tv;
public final class ClockPolicyTest {
    private static void equal(long wanted,long actual,String label){if(wanted!=actual)throw new AssertionError(label+": "+actual);}
    public static void main(String[] args){
        long date=1792896960000L;
        equal(date+100,ClockPolicy.sample(date,null,1000,1200),"Fresh response includes half latency");
        equal(date+2100,ClockPolicy.sample(date,"2",1000,1200),"Small cache age is accounted for");
        equal(0,ClockPolicy.sample(date,"1080",1000,1200),"Reject an 18-minute cached response");
        equal(0,ClockPolicy.sample(date,"-1",1000,1200),"Reject negative cache age");
        equal(0,ClockPolicy.sample(date,"bad",1000,1200),"Reject invalid cache age");
        equal(0,ClockPolicy.sample(0,null,1000,1200),"Reject missing Date header");
        equal(0,ClockPolicy.sample(date,null,1000,7000),"Reject slow samples");
        equal(0,ClockPolicy.sample(date,null,1200,1000),"Reject invalid monotonic interval");
        System.out.println("ClockPolicy: 8 checks passed");
    }
}
