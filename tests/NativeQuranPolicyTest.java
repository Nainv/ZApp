package com.zikra.tv;
public final class NativeQuranPolicyTest {
    private static int count;
    private static void check(boolean value){count++;if(!value)throw new AssertionError("Quran check "+count);}
    public static void main(String[] args){
        String opening="بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",verse="الم";
        check(NativeQuranPolicy.displayText(1,1,opening,opening).isEmpty());
        check(NativeQuranPolicy.displayText(2,1,opening+" "+verse,opening).equals(verse));
        check(NativeQuranPolicy.displayText(9,1,opening+verse,opening).equals(opening+verse));
        check(NativeQuranPolicy.displayText(27,30,opening,opening).equals(opening));
        check(NativeQuranPolicy.displayText(2,1,verse,opening).equals(verse));
        check(NativeQuranPolicy.ORNAMENT.equals("﴿ ﷽ ﴾"));check(NativeQuranPolicy.hasOpening(1,1));check(NativeQuranPolicy.hasOpening(2,1));check(!NativeQuranPolicy.hasOpening(9,1));check(!NativeQuranPolicy.hasOpening(27,30));
        java.util.HashSet<Integer> keys=new java.util.HashSet<>();for(int surah=1;surah<=114;surah++)for(int ayah=1;ayah<=286;ayah++)check(keys.add(NativeQuranPolicy.key(surah,ayah)));
        System.out.println("Native Quran: "+count+" checks passed");
    }
}
