package com.zikra.tv;
/** Display-only rules: original sacred text, recording timestamps and verse numbering are untouched. */
public final class NativeQuranPolicy {
    private NativeQuranPolicy(){}
    public static int key(int surah,int ayah){return surah*1000+ayah;}
    public static final String ORNAMENT="﴿ ﷽ ﴾";
    public static boolean hasOpening(int surah,int ayah){return ayah==1&&surah!=9;}
    public static String displayText(int surah,int ayah,String source,String basmala){
        if(surah==1&&ayah==1)return "";
        if(!hasOpening(surah,ayah)||basmala.isEmpty())return source;
        String value=source.trim();return value.startsWith(basmala)?value.substring(basmala.length()).trim():source;
    }
}
