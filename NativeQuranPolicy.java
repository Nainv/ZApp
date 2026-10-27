package com.zikra.tv;
/** Display-only rules: original sacred text, recording timestamps and verse numbering are untouched. */
public final class NativeQuranPolicy {
    private NativeQuranPolicy(){}
    public static int key(int surah,int ayah){return surah*1000+ayah;}
    public static String displayText(int surah,int ayah,String source,String basmala){
        if(ayah!=1||surah==1||surah==9||basmala.isEmpty())return source;
        String value=source.trim();return value.startsWith(basmala)?value.substring(basmala.length()).trim():source;
    }
}
