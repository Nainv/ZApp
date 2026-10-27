package com.zikra.tv;
public final class NativeTvPolicyTest {
 private static int count;
 private static void check(boolean value){count++;if(!value)throw new AssertionError("check "+count);}
 private static void rejects(String url,int number){try{NativeTvPolicy.audioUrl(url,number);throw new AssertionError("accepted "+url);}catch(IllegalArgumentException expected){count++;}}
 public static void main(String[] args){
  check(NativeTvPolicy.page(-1)==1);check(NativeTvPolicy.page(605)==604);check(NativeTvPolicy.page(319)==319);
  check(NativeTvPolicy.audioUrl("https://server.mp3quran.net/reader",1).equals("https://server.mp3quran.net/reader/001.mp3"));
  check(NativeTvPolicy.audioUrl("https://server.mp3quran.net/reader/",114).endsWith("/114.mp3"));
  rejects("http://example.org/",1);rejects("file:///data/",1);rejects("https://a@b/",1);rejects("https://b/?q=1",1);rejects("https://b/#x",1);rejects("https://b/",0);rejects("https://b/",115);
  check(NativeTvPolicy.matches("مشاري العَفَاسِي", "العفاسي"));check(NativeTvPolicy.matches("ماهر المعيقلي", "ماهر"));check(!NativeTvPolicy.matches("ماهر المعيقلي", "مشاري"));check(NativeTvPolicy.matches("Ibrahim Al-Akdar", "ibrahim"));
  check(NativeTvPolicy.ayahAt(new long[]{100,200,300},new int[]{1,2,3},99)==1);check(NativeTvPolicy.ayahAt(new long[]{100,200,300},new int[]{1,2,3},200)==2);check(NativeTvPolicy.ayahAt(new long[]{100,200,300},new int[]{1,2,3},999)==3);check(NativeTvPolicy.ayahAt(new long[0],new int[0],100)==0);
  double berlin=NativeTvPolicy.qibla(52.52,13.405);check(berlin>130&&berlin<140);check(NativeTvPolicy.qibla(-33.87,151.2)>=0);
  check(!NativeTvPolicy.exitOnBack(0,100));check(NativeTvPolicy.exitOnBack(100,2100));check(!NativeTvPolicy.exitOnBack(100,2101));check(!NativeTvPolicy.exitOnBack(200,100));
  check(!NativeTvPolicy.exitOnBack(false,2,100,101));check(!NativeTvPolicy.exitOnBack(true,1,100,101));check(!NativeTvPolicy.exitOnBack(true,0,0,101));check(NativeTvPolicy.exitOnBack(true,0,100,101));
  check(NativeTvPolicy.horizontalStep(true,true)==1);check(NativeTvPolicy.horizontalStep(true,false)==-1);check(NativeTvPolicy.horizontalStep(false,false)==1);
  check(NativeTvPolicy.stepVerse(1,-1,7)==1);check(NativeTvPolicy.stepVerse(7,1,7)==7);check(NativeTvPolicy.stepVerse(3,1,7)==4);
  System.out.println("Native TV: "+count+" checks passed");
 }
}
