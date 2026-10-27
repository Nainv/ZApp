package com.zikra.tv;

import java.net.URI;
import java.util.Locale;
/** Small presentation rules that can be verified without Android or a network. */
public final class NativeTvPolicy {
    private NativeTvPolicy(){}
    public static boolean exitOnBack(long last,long now){return last>0&&now>=last&&now-last<=2000;}
    public static int horizontalStep(boolean rtl,boolean left){return left?(rtl?1:-1):(rtl?-1:1);}
    public static int stepVerse(int current,int delta,int total){return Math.max(1,Math.min(Math.max(1,total),current+delta));}
    public static int page(int value){return Math.max(1,Math.min(604,value));}
    public static String audioUrl(String server,int surah){
        if(surah<1||surah>114)throw new IllegalArgumentException("surah");
        URI uri=URI.create(server);
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("server");
        return server+(server.endsWith("/")?"":"/")+String.format(Locale.US,"%03d.mp3",surah);
    }
    public static boolean matches(String name,String query){
        String normalized=normalize(name);for(String part:normalize(query).split(" +"))if(!normalized.contains(part))return false;return true;
    }
    private static String normalize(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).toLowerCase(Locale.ROOT).replaceAll("[\\u0300-\\u036f\\u064b-\\u065f\\u0670\\u06d6-\\u06ed\\u0640]","").replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ٱ','ا').replace('ى','ي').trim();}
    public static int ayahAt(long[] starts,int[] ayahs,long milliseconds){
        if(starts.length==0||starts.length!=ayahs.length)return 0;
        int low=0,high=starts.length-1;while(low<high){int middle=(low+high+1)/2;if(starts[middle]<=milliseconds)low=middle;else high=middle-1;}return ayahs[low];
    }
    public static double qibla(double latitude,double longitude){
        if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180)throw new IllegalArgumentException("location");
        double lat=Math.toRadians(latitude),delta=Math.toRadians(39.8262-longitude),kaaba=Math.toRadians(21.4225);
        return (Math.toDegrees(Math.atan2(Math.sin(delta),Math.cos(lat)*Math.tan(kaaba)-Math.sin(lat)*Math.cos(delta)))+360)%360;
    }
}
