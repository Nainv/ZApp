package com.zikra.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/** Live dashboard: summaries stay on Home; OK opens their full native screens. */
@androidx.annotation.RequiresApi(26)
final class NativeTvHome extends LinearLayout {
    interface Actions { void run(String action); }
    static final int WEATHER=0x00ea001,TASBEEH=0x00ea002,QURAN=0x00ea003,MEMORY=0x00ea004;
    private static final int FIRST_PRAYER=0x00ea010,PREVIOUS=0x00ea020,PLAY=0x00ea021,NEXT=0x00ea022,READ=0x00ea023,READER=0x00ea024;
    private static final int TEXT=0xffeff4ef,MUTED=0xff9bb3b3,ACCENT=0xff79c9b2;
    private final NativeTvMotion motion;
    private final Typeface font;
    private final Actions actions;
    private final java.util.function.Function<String,String> translate;
    private final TextView city,temperature,weatherDetail,count,upcoming,track,reciter,verse,remembrance,ticker;
    private final TextView[] times=new TextView[5];
    private final LinearLayout[] prayers=new LinearLayout[5];
    private final Button play;
    private long nextEpoch;
    private String nextName="";
    NativeTvHome(Context context,NativeTvMotion animation,Typeface face,Actions handler,java.util.function.Function<String,String> labels){
        super(context);motion=animation;font=face;actions=handler;translate=labels;setOrientation(VERTICAL);setClipChildren(false);setClipToPadding(false);
        setPadding(dp(7),dp(5),dp(7),dp(6));
        LinearLayout top=row();addView(top,new LayoutParams(-1,0,.85f));
        LinearLayout weather=panel(WEATHER,"weather",false);top.addView(weather,weighted(3));
        LinearLayout weatherLine=row();weatherLine.setGravity(Gravity.CENTER_VERTICAL);weather.addView(weatherLine,new LayoutParams(-1,0,1));
        LinearLayout description=new LinearLayout(context);description.setOrientation(VERTICAL);weatherLine.addView(description,new LayoutParams(0,-1,1));
        city=label("حدد الموقع",17,ACCENT,1);description.addView(city);
        weatherDetail=label("الطقس يظهر هنا مباشرة",14,MUTED,2);description.addView(weatherDetail,new LayoutParams(-1,0,1));
        temperature=label("— °C",31,TEXT,1);temperature.setGravity(Gravity.CENTER);weatherLine.addView(temperature,new LayoutParams(dp(130),-1));
        LinearLayout tasbeeh=panel(TASBEEH,"tasbeeh-add",false);top.addView(tasbeeh,weighted(1));
        tasbeeh.addView(label("التسبيح",14,ACCENT,1));count=label("0",29,TEXT,1);count.setGravity(Gravity.CENTER);tasbeeh.addView(count,new LayoutParams(-1,0,1));
        ticker=label("ألا بذكر الله تطمئن القلوب",14,MUTED,1);ticker.setGravity(Gravity.CENTER);LayoutParams tickerSize=new LayoutParams(-1,dp(24));tickerSize.setMargins(0,dp(3),0,dp(3));addView(ticker,tickerSize);
        LinearLayout prayerRow=row();addView(prayerRow,new LayoutParams(-1,0,.74f));String[] names={"الفجر","الظهر","العصر","المغرب","العشاء"};
        for(int i=0;i<5;i++){LinearLayout card=panel(FIRST_PRAYER+i,"prayer",true);prayers[i]=card;card.setPadding(dp(10),dp(7),dp(10),dp(7));card.setGravity(Gravity.CENTER);prayerRow.addView(card,weighted(1));TextView name=label(names[i],14,MUTED,1);name.setGravity(Gravity.CENTER);card.addView(name);times[i]=label("—",23,TEXT,1);times[i].setGravity(Gravity.CENTER);card.addView(times[i],new LayoutParams(-1,0,1));}
        upcoming=label("حدد الموقع لإظهار الصلاة القادمة",13,ACCENT,1);upcoming.setGravity(Gravity.CENTER);LayoutParams nextSize=new LayoutParams(-1,dp(24));nextSize.setMargins(0,dp(2),0,dp(2));addView(upcoming,nextSize);
        LinearLayout lower=row();LayoutParams lowerSize=new LayoutParams(-1,0,1.62f);lowerSize.topMargin=dp(3);addView(lower,lowerSize);
        LinearLayout quran=panel(QURAN,"quran",false);quran.setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);lower.addView(quran,weighted(3));
        LinearLayout playerHeading=row();playerHeading.setGravity(Gravity.CENTER_VERTICAL);quran.addView(playerHeading,new LayoutParams(-1,dp(24)));
        track=label("القرآن الكريم",18,TEXT,1);playerHeading.addView(track,new LayoutParams(0,-1,1));
        reciter=label("اختر القارئ ثم السورة",12,MUTED,1);quran.addView(reciter);
        verse=label("افتح فهرس القرآن أو تابع تلاوتك من آخر موضع.",23,TEXT,3);verse.setGravity(Gravity.CENTER_VERTICAL);verse.setAutoSizeTextTypeUniformWithConfiguration(16,23,1,android.util.TypedValue.COMPLEX_UNIT_SP);quran.addView(verse,new LayoutParams(-1,0,1));
        LinearLayout controls=row();controls.setGravity(Gravity.CENTER);quran.addView(controls,new LayoutParams(-1,dp(36)));
        Button reader=control(READER,"القارئ","readers"),previous=control(PREVIOUS,"‹","previous"),next=control(NEXT,"›","next");
        play=control(PLAY,"▶","play");Button read=control(READ,"المصحف","mushaf");
        controls.addView(reader,controlSize(1.4f));controls.addView(previous,controlSize(.7f));controls.addView(play,controlSize(.85f));controls.addView(next,controlSize(.7f));controls.addView(read,controlSize(1.4f));
        LinearLayout memory=panel(MEMORY,"memory",false);lower.addView(memory,weighted(2));memory.addView(label("ذِكْرَىٰ اليوم",16,ACCENT,1));
        remembrance=label("جارٍ تجهيز الأذكار المحفوظة…",21,TEXT,4);remembrance.setGravity(Gravity.CENTER_VERTICAL);remembrance.setAutoSizeTextTypeUniformWithConfiguration(15,21,1,android.util.TypedValue.COMPLEX_UNIT_SP);memory.addView(remembrance,new LayoutParams(-1,0,1));memory.addView(label("OK لقراءة الذكر كاملًا",11,MUTED,1));
        wire(weather,tasbeeh,quran,memory,reader,previous,next,read);
        tasbeeh.setOnLongClickListener(v->{actions.run("tasbeeh-reset");return true;});
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout row(){LinearLayout v=new LinearLayout(getContext());v.setOrientation(HORIZONTAL);v.setClipChildren(false);v.setClipToPadding(false);return v;}
    private LayoutParams weighted(float weight){LayoutParams p=new LayoutParams(0,-1,weight);p.setMargins(dp(5),dp(4),dp(5),dp(4));return p;}
    private LayoutParams controlSize(float weight){LayoutParams p=new LayoutParams(0,-1,weight);p.setMargins(dp(3),0,dp(3),0);return p;}
    private GradientDrawable background(boolean focused){GradientDrawable d=new GradientDrawable();d.setColor(focused?0xf0254147:0xe619282e);d.setCornerRadius(dp(16));return d;}
    private TextView label(String value,int size,int color,int lines){TextView v=new TextView(getContext());v.setText(translate.apply(value));v.setTextSize(size);v.setTextColor(color);v.setTypeface(font);v.setFontFeatureSettings("tnum");v.setMaxLines(lines);v.setEllipsize(TextUtils.TruncateAt.END);v.setIncludeFontPadding(false);return v;}
    private LinearLayout panel(int id,String action,boolean tile){LinearLayout v=new LinearLayout(getContext());v.setId(id);v.setOrientation(VERTICAL);v.setPadding(dp(16),dp(9),dp(16),dp(9));v.setBackground(background(false));v.setFocusable(true);v.setClickable(true);v.setClipChildren(false);v.setClipToPadding(false);v.setOnFocusChangeListener((view,on)->{view.setBackground(background(on));motion.focus(view,on,tile);});v.setOnClickListener(view->{motion.press(view);actions.run(action);});return v;}
    private Button control(int id,String title,String action){Button v=new Button(getContext());v.setId(id);v.setText(translate.apply(title));v.setTextSize(13);v.setTypeface(font);v.setAllCaps(false);v.setTextColor(TEXT);v.setPadding(0,0,0,0);v.setMinimumHeight(0);v.setMinimumWidth(0);v.setMinHeight(0);v.setMinWidth(0);v.setBackground(background(false));v.setOnFocusChangeListener((view,on)->motion.focus(view,on,false));v.setOnClickListener(view->{motion.press(view);actions.run(action);});return v;}
    private void wire(View weather,View tasbeeh,View quran,View memory,View reader,View previous,View next,View read){
        // Explicit vertical lanes prevent the large player/weather panels from stealing selection.
        weather.setNextFocusDownId(FIRST_PRAYER);tasbeeh.setNextFocusDownId(FIRST_PRAYER+4);
        for(int i=0;i<5;i++){prayers[i].setNextFocusUpId(i<3?WEATHER:TASBEEH);prayers[i].setNextFocusDownId(i<3?QURAN:MEMORY);}
        quran.setNextFocusUpId(FIRST_PRAYER+1);quran.setNextFocusDownId(PLAY);memory.setNextFocusUpId(FIRST_PRAYER+4);
        for(View control:new View[]{reader,previous,play,next,read})control.setNextFocusUpId(QURAN);
        boolean rtl=getResources().getConfiguration().getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;
        quran.setNextFocusLeftId(rtl?MEMORY:View.NO_ID);quran.setNextFocusRightId(rtl?View.NO_ID:MEMORY);memory.setNextFocusRightId(rtl?QURAN:View.NO_ID);memory.setNextFocusLeftId(rtl?View.NO_ID:QURAN);
    }
    private void set(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}
    void weather(String place,String temp,String detail){set(city,place);set(temperature,temp);set(weatherDetail,detail);}
    void prayer(String[] values,int nextIndex,String name,long epoch){for(int i=0;i<times.length;i++){set(times[i],values[i]);times[i].setTextColor(i==nextIndex?ACCENT:TEXT);}nextName=name;nextEpoch=epoch;}
    void clock(long now){if(nextEpoch<=now){set(upcoming,nextName.isEmpty()?"حدد الموقع لإظهار الصلاة القادمة":"جارٍ تحديث الصلاة القادمة…");return;}long seconds=Math.max(0,(nextEpoch-now)/1000);set(upcoming,nextName+" بعد "+String.format(Locale.US,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60));}
    void player(String name,String readerName,String text,boolean playing){set(track,name.isEmpty()?"القرآن الكريم":name);set(reciter,readerName.isEmpty()?"اختر القارئ ثم السورة":readerName);set(verse,text.isEmpty()?"افتح فهرس القرآن أو تابع تلاوتك من آخر موضع.":text);set(play,playing?"Ⅱ":"▶");}
    void remembrance(String value){set(remembrance,value);set(ticker,value);}
    void tasbeeh(String value){set(count,value);}
    void restoreFocus(int id){View target=findViewById(id);if(target==null)target=findViewById(WEATHER);if(target!=null)target.requestFocus();}
}
