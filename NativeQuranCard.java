package com.zikra.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.StaticLayout;
import android.text.Layout;
import android.text.TextDirectionHeuristics;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.HashMap;
import java.util.List;

/** Continuous Quran text in one card; only the active verse's paint spans change during playback. */
@androidx.annotation.RequiresApi(26)
final class NativeQuranCard extends LinearLayout {
    private final TextView heading,subtitle,text;
    private final ScrollView scroll;
    private final NativeQuranBook book;
    private final HashMap<Integer,int[]> ranges=new HashMap<>();
    private Spannable passage;
    private BackgroundColorSpan shade;
    private ForegroundColorSpan ink;
    private boolean light;
    private int page=-1,activeKey=-1,activeStart=-1,activeEnd=-1,preferredSize=30;
    private final Runnable fit=this::fitPage;
    private String reader="";
    NativeQuranCard(Context context,Typeface uiFont,NativeQuranBook index,int size,boolean lightPage){
        super(context);book=index;setOrientation(VERTICAL);setLayoutDirection(View.LAYOUT_DIRECTION_RTL);setPadding(dp(22),dp(12),dp(22),dp(12));
        heading=new TextView(context);heading.setTypeface(uiFont);heading.setTextSize(24);heading.setGravity(Gravity.CENTER);addView(heading,new LayoutParams(-1,dp(34)));
        subtitle=new TextView(context);subtitle.setTypeface(uiFont);subtitle.setTextSize(13);subtitle.setGravity(Gravity.CENTER);addView(subtitle,new LayoutParams(-1,dp(24)));
        scroll=new ScrollView(context);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setDefaultFocusHighlightEnabled(false);scroll.setFocusable(true);scroll.setPadding(0,dp(14),0,dp(10));addView(scroll,new LayoutParams(-1,0,1));
        text=new TextView(context);try{text.setTypeface(Typeface.createFromAsset(context.getAssets(),"assets/fonts/AmiriQuran.ttf"));}catch(Exception ignored){}
        text.setTextDirection(View.TEXT_DIRECTION_RTL);text.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);text.setGravity(Gravity.CENTER);text.setLineSpacing(dp(2),1.12f);text.setPadding(dp(8),dp(12),dp(8),dp(20));text.setTextIsSelectable(false);text.setIncludeFontPadding(true);scroll.addView(text,new ScrollView.LayoutParams(-1,-2));scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)scheduleFit();});style(size,lightPage);
    }
    ScrollView scroller(){return scroll;}
    void style(int size,boolean lightPage){
        light=lightPage;GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,light?new int[]{0xfff5f1e6,0xffebe7dd}:new int[]{0xf0263442,0xf00d1b2a});background.setCornerRadius(dp(18));setBackground(background);
        heading.setTextColor(light?0xff765221:0xffe5c77b);subtitle.setTextColor(light?0xff62717a:0xff9aaaba);text.setTextColor(light?0xff172a37:0xffeff4f3);preferredSize=Math.max(22,Math.min(48,size));scheduleFit();
        if(passage!=null&&activeStart>=0)paintActive(activeStart,activeEnd);
    }
    void show(int number,int surah,int ayah,boolean timed,String readerName){
        reader=readerName==null?"":readerName;
        if(page!=number){page=number;buildPage();scroll.scrollTo(0,0);activeKey=Integer.MIN_VALUE;}
        subtitle.setText((reader.isEmpty()?"":reader+"  ·  ")+"الصفحة "+page+" / 604");
        int key=timed?NativeQuranPolicy.key(surah,ayah):-1;
        if(key==activeKey)return;activeKey=key;int[] range=ranges.get(key);activeStart=range==null?-1:range[0];activeEnd=range==null?-1:range[1];
        paintActive(activeStart,activeEnd);if(activeStart>=0)centerActive();
    }
    private void buildPage(){
        ranges.clear();SpannableStringBuilder content=new SpannableStringBuilder();List<NativeQuranBook.Verse> verses=book==null?java.util.Collections.emptyList():book.page(page);
        heading.setText(verses.isEmpty()?"المصحف الشريف":verses.get(0).name);int previous=-1;
        for(NativeQuranBook.Verse verse:verses){
            if(previous!=-1&&previous!=verse.surah){content.append("\n\n");int titleStart=content.length();content.append(verse.name).append("\n\n");content.setSpan(new ForegroundColorSpan(0xffc9ad70),titleStart,content.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
            if(verse.number==1&&verse.surah!=1&&verse.surah!=9&&!book.basmala.isEmpty())content.append(book.basmala).append("\n");
            String value=NativeQuranPolicy.displayText(verse.surah,verse.number,verse.text,book.basmala);
            int start=content.length();content.append(value.replace('\n',' ').trim()).append(" ");int marker=content.length();content.append("\ufffc");content.setSpan(new VerseMark(verse.number),marker,marker+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ranges.put(NativeQuranPolicy.key(verse.surah,verse.number),new int[]{start,content.length()});content.append("  ");previous=verse.surah;
        }
        if(content.length()==0)content.append("جارٍ تجهيز نص المصحف…");text.setText(content,TextView.BufferType.SPANNABLE);passage=(Spannable)text.getText();shade=null;ink=null;activeStart=-1;activeEnd=-1;scheduleFit();
    }
    private void paintActive(int start,int end){
        if(passage==null)return;if(shade!=null)passage.removeSpan(shade);if(ink!=null)passage.removeSpan(ink);shade=null;ink=null;
        if(start<0||end<=start)return;
        shade=new BackgroundColorSpan(light?0xffccdafa:0xff304e86);ink=new ForegroundColorSpan(light?0xff102438:0xfffbfcff);passage.setSpan(shade,start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);passage.setSpan(ink,start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    private void centerActive(){
        text.post(()->{android.text.Layout layout=text.getLayout();if(layout==null||activeStart<0)return;int first=layout.getLineForOffset(activeStart),last=layout.getLineForOffset(Math.max(activeStart,activeEnd-1));int top=layout.getLineTop(first)+text.getPaddingTop(),bottom=layout.getLineBottom(last)+text.getPaddingTop();if(top<scroll.getScrollY()+dp(12)||bottom>scroll.getScrollY()+scroll.getHeight()-dp(12))scroll.smoothScrollTo(0,Math.max(0,(top+bottom-scroll.getHeight())/2));});
    }
    private void scheduleFit(){text.removeCallbacks(fit);text.post(fit);}
    private void fitPage(){
        if(passage==null)return;
        int width=scroll.getWidth()-scroll.getPaddingLeft()-scroll.getPaddingRight()-text.getPaddingLeft()-text.getPaddingRight();
        int height=scroll.getHeight()-scroll.getPaddingTop()-scroll.getPaddingBottom()-text.getPaddingTop()-text.getPaddingBottom();
        if(width<=0||height<=0)return;
        android.text.TextPaint paint=new android.text.TextPaint(text.getPaint());float scale=getResources().getDisplayMetrics().scaledDensity;
        int chosen=12;for(int size=preferredSize;size>=12;size--){paint.setTextSize(size*scale);StaticLayout layout=StaticLayout.Builder.obtain(passage,0,passage.length(),paint,width).setAlignment(Layout.Alignment.ALIGN_CENTER).setTextDirection(TextDirectionHeuristics.RTL).setIncludePad(true).setLineSpacing(dp(2),1.12f).build();if(layout.getHeight()<=height){chosen=size;break;}}
        text.setTextSize(chosen);scroll.scrollTo(0,0);
    }
    /** Center the ornament on the Arabic text's baseline metrics; scale it with fitted text. */
    private final class VerseMark extends ReplacementSpan {
        private final NativeAyahMark mark;
        VerseMark(int number){mark=new NativeAyahMark(getContext(),number);}
        @Override public int getSize(Paint paint,CharSequence value,int start,int end,Paint.FontMetricsInt metrics){return Math.round(paint.getTextSize()*.90f);}
        @Override public void draw(Canvas canvas,CharSequence value,int start,int end,float x,int top,int baseline,int bottom,Paint paint){int size=Math.round(paint.getTextSize()*.90f);Paint.FontMetrics fm=paint.getFontMetrics();float center=baseline+(fm.ascent+fm.descent)/2;canvas.save();canvas.translate(x,center-size/2f);mark.setBounds(0,0,size,size);mark.draw(canvas);canvas.restore();}
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
