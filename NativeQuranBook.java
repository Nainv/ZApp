package com.zikra.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable page index. Quran text and numbering remain exactly as bundled. */
final class NativeQuranBook {
    static final class Verse {
        final int surah,number,page;
        final String name,text;
        Verse(int s,int n,int p,String title,String value){surah=s;number=n;page=p;name=title;text=value;}
    }
    private final List<List<Verse>> pages=new ArrayList<>(605);
    private final int[][] versePages=new int[115][];
    final String basmala;
    NativeQuranBook(JSONArray surahs){
        for(int i=0;i<=604;i++)pages.add(new ArrayList<>());
        String first="";
        for(int i=0;i<surahs.length();i++){
            JSONObject chapter=surahs.optJSONObject(i);if(chapter==null)continue;int number=chapter.optInt("number");JSONArray verses=chapter.optJSONArray("ayahs");if(verses==null||number<1||number>114)continue;
            versePages[number]=new int[verses.length()+1];
            for(int j=0;j<verses.length();j++){
                JSONObject verse=verses.optJSONObject(j);if(verse==null)continue;int page=verse.optInt("page"),ayah=verse.optInt("numberInSurah");if(page<1||page>604||ayah<1||ayah>=versePages[number].length)continue;
                String text=verse.optString("text");pages.get(page).add(new Verse(number,ayah,page,chapter.optString("name"),text));versePages[number][ayah]=page;if(number==1&&ayah==1)first=text.trim();
            }
        }
        basmala=first;
    }
    List<Verse> page(int number){return number>=1&&number<=604?Collections.unmodifiableList(pages.get(number)):Collections.emptyList();}
    int pageOf(int surah,int ayah){if(surah<1||surah>114||versePages[surah]==null||ayah<1||ayah>=versePages[surah].length)return 1;return Math.max(1,versePages[surah][ayah]);}
}
