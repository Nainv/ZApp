package com.zikra.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Forecast cards share the same colors, native motion and remote selection as Home. */
@androidx.annotation.RequiresApi(25)
final class NativeWeatherView extends LinearLayout {
    private final Typeface font;
    private final NativeTvMotion motion;
    private final JSONObject data,daily;
    private final LinearLayout details;
    private final Button[] days;
    private final IntConsumer changed;
    private final boolean rtl;
    private int selected;
    NativeWeatherView(Context context,Typeface typeface,NativeTvMotion animation,JSONObject forecast,String place,boolean arabic,int day,IntConsumer change,Runnable refresh,Runnable location){
        super(context);font=typeface;motion=animation;data=forecast==null?new JSONObject():forecast;daily=data.optJSONObject("daily");changed=change;rtl=arabic;setOrientation(VERTICAL);setLayoutDirection(rtl?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);setClipChildren(false);setClipToPadding(false);setPadding(dp(8),dp(6),dp(8),dp(8));
        JSONObject current=data.optJSONObject("current");TextView currentText=label(place+"  ·  "+(current==null?"—":degrees(current.optDouble("temperature_2m")))+"\n"+(current==null?"اختر المدينة لإظهار الطقس":condition(current.optInt("weather_code"))),24,0xffeff4ef);currentText.setPadding(dp(18),dp(10),dp(18),dp(10));currentText.setBackground(background(false));addView(currentText,new LayoutParams(-1,dp(76)));
        JSONArray dates=daily==null?null:daily.optJSONArray("time");int count=dates==null?0:dates.length();days=new Button[count];
        HorizontalScrollView scroller=new HorizontalScrollView(context);scroller.setHorizontalScrollBarEnabled(false);scroller.setClipToPadding(false);scroller.setPadding(dp(8),dp(10),dp(8),dp(10));scroller.setLayoutDirection(rtl?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);LinearLayout strip=new LinearLayout(context);strip.setLayoutDirection(rtl?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);strip.setClipChildren(false);scroller.addView(strip,new HorizontalScrollView.LayoutParams(-2,-1));addView(scroller,new LayoutParams(-1,dp(148)));
        Locale locale=Locale.forLanguageTag(rtl?"ar-u-nu-latn":"en-u-nu-latn");
        for(int i=0;i<count;i++){final int index=i;String iso=dates.optString(i);String date=iso;try{date=LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE  dd/MM",locale));}catch(Exception ignored){}
            Button card=new Button(context);days[i]=card;if(android.os.Build.VERSION.SDK_INT>=26)card.setDefaultFocusHighlightEnabled(false);card.setTypeface(font);card.setTextColor(0xffeff4ef);card.setAllCaps(false);card.setTextSize(15);card.setGravity(Gravity.CENTER);card.setMaxLines(3);card.setIncludeFontPadding(false);card.setPadding(dp(7),dp(4),dp(7),dp(4));card.setBackground(background(false));card.setText(date+"\n"+symbol(value("weather_code",i))+"\n"+degrees(value("temperature_2m_min",i))+" / "+degrees(value("temperature_2m_max",i)));
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(dp(146),-1);size.setMargins(dp(6),0,dp(6),0);strip.addView(card,size);card.setOnFocusChangeListener((view,on)->{view.setBackground(background(on));motion.focus(view,on,false);if(on){selected=index;changed.accept(index);drawDetails();}});card.setOnClickListener(view->{selected=index;drawDetails();});
        }
        scroller.post(()->{int available=Math.max(dp(1),scroller.getWidth()-scroller.getPaddingLeft()-scroller.getPaddingRight());int width=Math.max(dp(142),Math.min(dp(220),(available-dp(40))/4));for(Button dayCard:days)if(dayCard!=null){LinearLayout.LayoutParams size=(LinearLayout.LayoutParams)dayCard.getLayoutParams();size.width=width;dayCard.setLayoutParams(size);}});
        details=new LinearLayout(context);details.setOrientation(VERTICAL);details.setPadding(dp(18),dp(12),dp(18),dp(12));details.setBackground(background(false));addView(details,new LayoutParams(-1,0,1));
        LinearLayout controls=new LinearLayout(context);Button update=button("تحديث الطقس",refresh),choose=button("اختيار المدينة",location);LayoutParams controlSize=new LayoutParams(0,dp(42),1);controlSize.setMargins(dp(5),dp(10),dp(5),0);controls.addView(update,controlSize);LayoutParams second=new LayoutParams(controlSize);controls.addView(choose,second);addView(controls);
        selected=Math.max(0,Math.min(Math.max(0,count-1),day));drawDetails();if(count>0)days[selected].post(()->days[selected].requestFocus());else choose.post(choose::requestFocus);
    }
    boolean move(int delta){if(days.length==0||!days[selected].hasFocus())return false;selected=Math.floorMod(selected+delta,days.length);days[selected].requestFocus();return true;}
    private void drawDetails(){if(details==null)return;details.removeAllViews();if(daily==null){details.addView(label("لم تصل بيانات الطقس بعد.",19,0xff9bb3b3));return;}
        String date=daily.optJSONArray("time").optString(selected);LinearLayout summary=new LinearLayout(getContext());summary.setOrientation(HORIZONTAL);summary.setGravity(Gravity.CENTER_VERTICAL);summary.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);TextView metrics=label("الصغرى "+degrees(value("temperature_2m_min",selected))+"   ·   الكبرى "+degrees(value("temperature_2m_max",selected))+"   ·   احتمال المطر "+percent(value("precipitation_probability_max",selected)),15,0xffeff4ef);metrics.setSingleLine(true);metrics.setEllipsize(android.text.TextUtils.TruncateAt.END);summary.addView(metrics,new LayoutParams(0,dp(30),1));TextView statusLabel=label(condition((int)value("weather_code",selected)),19,0xff79c9b2);statusLabel.setSingleLine(true);statusLabel.setGravity(Gravity.CENTER);summary.addView(statusLabel,new LayoutParams(-2,dp(30)));details.addView(summary,new LayoutParams(-1,dp(32)));
        JSONObject hourly=data.optJSONObject("hourly");JSONArray hours=hourly==null?null:hourly.optJSONArray("time");if(hours==null)return;
        HorizontalScrollView scroll=new HorizontalScrollView(getContext());scroll.setHorizontalScrollBarEnabled(false);scroll.setLayoutDirection(rtl?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);LinearLayout row=new LinearLayout(getContext());row.setLayoutDirection(rtl?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);scroll.addView(row);
        JSONArray sunrises=daily.optJSONArray("sunrise"),sunsets=daily.optJSONArray("sunset");String sunrise=sunrises==null?"":sunrises.optString(selected),sunset=sunsets==null?"":sunsets.optString(selected);
        for(int i=0;i<hours.length();i++){String time=hours.optString(i);if(!time.startsWith(date))continue;String hourKey=time.length()>=13?time.substring(0,13):time;boolean isSunrise=sunrise.length()>=13&&hourKey.equals(sunrise.substring(0,13)),isSunset=sunset.length()>=13&&hourKey.equals(sunset.substring(0,13));JSONArray temperatures=hourly.optJSONArray("temperature_2m");String hourLabel=(isSunrise?"☀ ↑\n":isSunset?"☀ ↓\n":"")+(time.length()>15?time.substring(11,16):time);TextView hour=label(hourLabel,14,0xff9bb3b3);hour.setText(hour.getText()+"\n"+degrees(temperatures==null?Double.NaN:temperatures.optDouble(i)));hour.setGravity(Gravity.CENTER);hour.setPadding(dp(14),dp(10),dp(14),dp(6));row.addView(hour);}
        details.addView(scroll,new LayoutParams(-1,0,1));
    }
    private double value(String key,int index){JSONArray values=daily==null?null:daily.optJSONArray(key);return values==null?Double.NaN:values.optDouble(index);}
    private String degrees(double value){return Double.isFinite(value)?String.format(Locale.US,"%.0f°",value):"—";}
    private String percent(double value){return Double.isFinite(value)?String.format(Locale.US,"%.0f%%",value):"—";}
    private String symbol(double value){int code=(int)value;if(code==0)return "☀";if(code<=3)return "☁";if(code>=71&&code<=86)return "❄";return "☂";}
    private String condition(int code){if(code==0)return "سماء صافية";if(code<=3)return "غائم جزئيًا";if(code<=48)return "ضباب";if(code>=95)return "عواصف رعدية";if(code>=71&&code<=77||code==85||code==86)return "ثلوج";return "أمطار";}
    private TextView label(String value,int size,int color){TextView view=new TextView(getContext());view.setText(value);view.setTypeface(font);view.setTextSize(size);view.setTextColor(color);return view;}
    private Button button(String label,Runnable run){Button view=new Button(getContext());if(android.os.Build.VERSION.SDK_INT>=26)view.setDefaultFocusHighlightEnabled(false);view.setText(label);view.setTypeface(font);view.setTextColor(0xffeff4ef);view.setAllCaps(false);view.setBackground(background(false));view.setOnFocusChangeListener((v,on)->motion.focus(v,on,false));view.setOnClickListener(v->run.run());return view;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(boolean focused){GradientDrawable d=new GradientDrawable();d.setColor(focused?0xff294950:0xe619282e);d.setCornerRadius(dp(16));return d;}
}
