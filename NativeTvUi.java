package com.zikra.tv;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Television presentation uses Android views only. The old web presentation is mobile-only.
 * No JavaScript runs for TV navigation, effects, reading, settings or playback. */
@androidx.annotation.RequiresApi(26)
final class NativeTvUi {
    private static final int TEXT=0xffeff4ef,MUTED=0xff9bb3b3,ACCENT=0xff79c9b2;
    private static final String[] PRAYERS={"الفجر","الظهر","العصر","المغرب","العشاء"};
    private final MainActivity owner;
    private final SharedPreferences data;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newFixedThreadPool(2);
    private final FrameLayout root;
    private final LinearLayout content;
    private final TvSkyView sky;
    private final NativeTvMotion motion;
    private final TextView status,clock;
    private final Typeface uiFont;
    private final ArrayDeque<String> history=new ArrayDeque<>();
    private final HashMap<String,Integer> selections=new HashMap<>();
    private RecyclerView grid;
    private NativeTvHome homeDashboard;
    private int homeFocusId=NativeTvHome.WEATHER;
    private long homeMinute=-1,weatherFetched;
    private String homeSchedule="";
    private boolean weatherLoading;

    private ScrollView bodyScroll;
    private LinearLayout pageActions;
    private Button sideLeft,sideRight;
    private NativeWeatherView weatherPage;
    private int weatherDay;
    private long lastBackAt,nextMemoryAt=android.os.SystemClock.elapsedRealtime()+15000;
    private android.graphics.Bitmap brandBitmap;
    private android.app.Dialog choicesDialog;
    private MediaPlayer previewPlayer;
    private boolean resumeAfterPreview,seekingVerse,previewSession;
    private int pendingVerse;
    private long seekTargetMs;

    private String page="home";
    private int selected,readPage=1,currentSurah=1,memoryIndex;
    private JSONArray surahs,memories,readers=new JSONArray();
    private JSONObject weather,translations=new JSONObject(),playbackReader=new JSONObject();
    private MediaPlayer player;
    private boolean prepared,paused=true,closed,resumed;
    private String playingUrl="",playingName="",readerQuery="";
    private int currentVerse=1,renderedVerse=-1;
    private long[] verseStarts=new long[0];
    private int[] verseNumbers=new int[0];
    private long playbackSequence;
    private NativeUpdateNotice updateBadge;
    private JSONObject runtime=new JSONObject();
    private String updatePhase="idle";
    private int updateProgress=-1;
    private long lastUpdateCheck;
    private NativeQuranCard quranCard;
    private NativeQuranBook quranBook;

    private long lastBookmark;
    private final Runnable audioTick=new Runnable(){public void run(){
        if(closed||!resumed||!prepared||player==null)return;
        try{if(seekingVerse){main.postDelayed(this,200);return;}int at=player.getCurrentPosition();int verse=NativeTvPolicy.ayahAt(verseStarts,verseNumbers,at);
            if(pendingVerse==0&&verse>0&&verse!=currentVerse){currentVerse=verse;if(page.equals("player")||page.equals("reading"))drawPlayerText();refreshDashboard();}
            if(page.equals("home"))refreshHomeQuran();if(android.os.SystemClock.elapsedRealtime()-lastBookmark>=5000){savePlayback();lastBookmark=android.os.SystemClock.elapsedRealtime();}
        }catch(Exception ignored){}main.postDelayed(this,200);
    }};
    private WebView migration;
    private LocationListener gps;
    private long requestId;
    private final Runnable tick=new Runnable(){public void run(){if(closed||!resumed)return;clock.setText(format("HH:mm",AdhanScheduler.now(owner)));sky.update();long elapsed=android.os.SystemClock.elapsedRealtime();if(elapsed-lastUpdateCheck>=60000){lastUpdateCheck=elapsed;owner.checkNativeUpdate();}if((page.equals("home")||page.equals("memory"))&&memories!=null&&memories.length()>1&&elapsed>=nextMemoryAt){memoryIndex=(memoryIndex+1)%memories.length();nextMemoryAt=elapsed+15000;if(page.equals("memory"))render(false);}refreshDashboard();main.postDelayed(this,1000);}};
    private static final class Tile {final String icon,title,detail;final Runnable run;Tile(String i,String t,String d,Runnable r){icon=i;title=t;detail=d;run=r;}}
    NativeTvUi(MainActivity value) {
        owner=value;data=owner.getSharedPreferences("native_tv_data",0);
        try(InputStream image=owner.getAssets().open("assets/images/zikra-dome.png")){brandBitmap=android.graphics.BitmapFactory.decodeStream(image);}catch(Exception ignored){}
        uiFont=owner.getResources().getFont(R.font.outfit);
        root=new FrameLayout(owner);root.setBackgroundColor(Color.BLACK);root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sky=new TvSkyView(owner,null);root.addView(sky,new FrameLayout.LayoutParams(-1,-1));sky.setIntensity(65);
        content=new LinearLayout(owner);content.setOrientation(LinearLayout.VERTICAL);content.setClipChildren(false);content.setClipToPadding(false);content.setPadding(dp(32),dp(20),dp(32),dp(18));root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        motion=new NativeTvMotion(root);
        clock=text("",24,MUTED);status=text("",15,ACCENT);
        status.setPadding(dp(12),dp(8),dp(12),dp(8));status.setBackground(shape(0xed102829,12));status.setVisibility(View.GONE);
        FrameLayout.LayoutParams bottom=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);bottom.bottomMargin=dp(16);root.addView(status,bottom);
    }
    void show(){owner.setContentView(root);render(false);migrateOnce();loadContent();loadReaders();loadWeather(true);}
    private int dp(float n){return Math.round(n*owner.getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,int color){TextView v=new TextView(owner);v.setText(tr(value));v.setTextColor(color);v.setTextSize(size);v.setFontFeatureSettings("tnum");v.setTypeface(uiFont);return v;}
    private String pref(String key,String fallback){return data.getString(key,fallback);}
    private int number(String key,int fallback){try{return Integer.parseInt(pref(key,String.valueOf(fallback)));}catch(Exception e){return fallback;}}
    private void put(String key,String value){data.edit().putString(key,value).apply();}
    private String format(String pattern,long time){SimpleDateFormat f=new SimpleDateFormat(pattern,new Locale.Builder().setLocale(Locale.forLanguageTag(pref("zikra_language","ar"))).setUnicodeLocaleKeyword("nu","latn").build());f.setTimeZone(TimeZone.getTimeZone(AdhanScheduler.configuration(owner).optString("zone",TimeZone.getDefault().getID())));return f.format(new Date(time));}
    private String tr(String ar){String language=pref("zikra_language","ar");if(language.equals("ar"))return ar;JSONArray translated=translations.optJSONArray(ar);if(translated!=null)return translated.optString(language.equals("de")?1:0,ar);String[][] labels={{"الرئيسية","Home","Startseite"},{"الإعدادات","Settings","Einstellungen"},{"القرآن الكريم","Quran","Koran"},{"المصحف","Read Quran","Koran lesen"},{"الأذكار","Remembrance","Gedenken"},{"مواقيت الصلاة","Prayer times","Gebetszeiten"},{"الطقس","Weather","Wetter"},{"القبلة","Qibla","Qibla"},{"الموقع والمسجد","Location & mosque","Standort & Moschee"},{"اللغة","Language","Sprache"},{"تحديث التطبيق","Update app","App aktualisieren"},{"رجوع","Back","Zurück"},{"التسبيح","Tasbih","Tasbih"},{"تشغيل / إيقاف","Play / Pause","Wiedergabe / Pause"}};for(String[] row:labels)if(row[0].equals(ar))return row[language.equals("de")?2:1];return ar;}
    private Button button(String label,Runnable action){Button v=new Button(owner);v.setId(View.generateViewId());v.setFocusable(true);v.setFocusableInTouchMode(true);v.setDefaultFocusHighlightEnabled(false);v.setMinHeight(0);v.setMinimumHeight(0);v.setText(tr(label));v.setTextSize(15);v.setTypeface(uiFont);v.setTextColor(TEXT);v.setAllCaps(false);v.setBackground(shape(0x88233138,18));v.setPadding(dp(12),0,dp(12),0);v.setOnFocusChangeListener((view,on)->{view.setBackground(shape(on?0xff294950:0x88233138,18));motion.focus(view,on,false);});v.setOnClickListener(view->{motion.press(view);action.run();});return v;}
    private void route(String target){lastBackAt=0;rememberSelection();history.push(page);page=target;render(false);}
    private void rememberSelection(){if(homeDashboard!=null){View focused=homeDashboard.findFocus();if(focused!=null&&focused.getId()!=View.NO_ID)homeFocusId=focused.getId();}if(grid!=null){View v=grid.findFocus();if(v!=null){View row=grid.findContainingItemView(v);if(row!=null)selections.put(page,grid.getChildAdapterPosition(row));}}}
    private boolean rtl(){return pref("zikra_language","ar").equals("ar");}
    void back(){
        if(!AdhanService.activePrayer.isEmpty()){AdhanService.stop(owner);return;}
        if(!history.isEmpty()){lastBackAt=0;rememberSelection();if(page.equals("weather"))weatherDay=0;page=history.pop();render(true);return;}
        if(!page.equals("home")){lastBackAt=0;page="home";render(true);return;}
        long now=android.os.SystemClock.elapsedRealtime();if(NativeTvPolicy.exitOnBack(page.equals("home"),history.size(),lastBackAt,now)){owner.finish();return;}lastBackAt=now;
    }
    private void render(boolean returning){
        if(closed)return;requestId++;motion.hide();if(updateBadge!=null)updateBadge.stopPulse();updateBadge=null;content.removeAllViews();removeSideNavigation();grid=null;homeDashboard=null;weatherPage=null;pageActions=null;bodyScroll=null;quranCard=null;
        content.setPadding(dp(page.equals("reading")?16:32),dp(page.equals("reading")?6:20),dp(page.equals("reading")?16:32),dp(page.equals("reading")?8:18));
        content.setLayoutDirection(pref("zikra_language","ar").equals("ar")?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);
        LinearLayout header=new LinearLayout(owner);header.setClipChildren(false);header.setClipToPadding(false);header.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);header.setGravity(Gravity.CENTER_VERTICAL);content.addView(header,new LinearLayout.LayoutParams(-1,dp(page.equals("reading")?42:50)));
        if(clock.getParent()!=null)((ViewGroup)clock.getParent()).removeView(clock);
        if(page.equals("home")){
            Button settings=button("⚙",()->route("settings"));settings.setNextFocusDownId(NativeTvHome.WEATHER);settings.setContentDescription("الإعدادات");header.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(40)));
            clock.setGravity(Gravity.CENTER);header.addView(clock,new LinearLayout.LayoutParams(dp(92),-2));
            Button mushaf=button("المصحف",()->route("mushaf"));mushaf.setNextFocusDownId(NativeTvHome.WEATHER);settings.setNextFocusRightId(mushaf.getId());mushaf.setNextFocusLeftId(settings.getId());header.addView(mushaf,new LinearLayout.LayoutParams(dp(90),dp(40)));
            updateBadge=new NativeUpdateNotice(owner,uiFont,motion,()->{if(!updateBusy()){updatePhase="checking";updateProgress=-1;refreshUpdateBadge();owner.startNativeUpdate();}});header.addView(updateBadge,new LinearLayout.LayoutParams(dp(142),dp(40)));refreshUpdateBadge();
        }else{Button back=button("رجوع",this::back);header.addView(back,new LinearLayout.LayoutParams(dp(90),dp(40)));}
        View space=new View(owner);header.addView(space,new LinearLayout.LayoutParams(0,1,1));
        TextView name=text("ذِكْرَىٰ",27,TEXT);header.addView(name,new LinearLayout.LayoutParams(-2,-2));
        android.widget.ImageView logo=new android.widget.ImageView(owner);if(brandBitmap!=null)logo.setImageBitmap(brandBitmap);logo.setContentDescription("ذِكرى");logo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams logoSize=new LinearLayout.LayoutParams(dp(46),dp(44));logoSize.leftMargin=dp(10);header.addView(logo,logoSize);
        TextView subtitle=text(page.equals("home")?format("EEE, dd MMM yyyy",AdhanScheduler.now(owner)):tr(title(page)),14,MUTED);subtitle.setPadding(0,dp(4),0,dp(10));if(!page.equals("reading"))content.addView(subtitle);
        if(page.equals("home")){
            homeDashboard=new NativeTvHome(owner,motion,uiFont,this::homeAction,this::tr,rtl());
            content.addView(homeDashboard,new LinearLayout.LayoutParams(-1,0,1));homeMinute=-1;homeSchedule="";refreshDashboard();
            NativeTvHome dashboard=homeDashboard;dashboard.setHeaderNavigation(header.getChildAt(0).getId(),header.getChildAt(2).getId());dashboard.post(()->{if(dashboard==homeDashboard)dashboard.restoreFocus(homeFocusId);});
        }
        else if(page.equals("player")){playerPage();}
        else if(page.equals("reading")){reading();}
        else if(page.equals("memory")){memory();}
        else if(page.equals("qibla")){qibla();}
        else if(page.equals("weather")){weather();}
        else grid(tiles(),page.equals("home")?3:page.equals("surahs")||page.equals("mushaf")?4:2);
        if(page.equals("reading")||page.equals("memory"))addSideNavigation();
        motion.enter(content,returning);
    }
    private String title(String key){switch(key){case "home":return "الرئيسية";case "settings":return "الإعدادات";case "surahs":case "player":return "القرآن الكريم";case "mushaf":case "reading":return "المصحف";case "memory":return "الأذكار";case "prayer":return "مواقيت الصلاة";case "weather":return "الطقس";case "qibla":return "القبلة";case "readers":return "اختيار القارئ";case "location":return "الموقع والمسجد";case "adhan":return "الأذان والتنبيهات";default:return key;}}
    private void grid(List<Tile> tiles,int columns){
        grid=new RecyclerView(owner);grid.setLayoutDirection(rtl()?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);grid.setDefaultFocusHighlightEnabled(false);grid.setLayoutManager(new GridLayoutManager(owner,columns));grid.setHasFixedSize(true);grid.setItemAnimator(null);grid.setClipChildren(false);grid.setClipToPadding(false);grid.setPadding(dp(12),dp(14),dp(12),dp(18));
        content.addView(grid,new LinearLayout.LayoutParams(-1,0,1));grid.setAdapter(new Cards(tiles));
        grid.addOnScrollListener(new RecyclerView.OnScrollListener(){public void onScrolled(RecyclerView rv,int dx,int dy){View v=rv.findFocus();if(v!=null)motion.position(v);}});
        int wanted=Math.min(Math.max(0,selections.getOrDefault(page,0)),tiles.size()-1);
        if(wanted>=0){grid.scrollToPosition(wanted);final RecyclerView target=grid;target.post(()->{RecyclerView.ViewHolder h=target.findViewHolderForAdapterPosition(wanted);if(h!=null)h.itemView.requestFocus();});}
    }
    private final class Cards extends RecyclerView.Adapter<Cards.Holder>{
        final List<Tile> items;Cards(List<Tile> rows){items=rows;setHasStableIds(true);}public long getItemId(int p){return p;}public int getItemCount(){return items.size();}
        class Holder extends RecyclerView.ViewHolder{final TextView icon,label,detail;Holder(LinearLayout v,TextView i,TextView t,TextView d){super(v);icon=i;label=t;detail=d;}}
        public Holder onCreateViewHolder(ViewGroup parent,int type){LinearLayout row=new LinearLayout(owner);row.setOrientation(LinearLayout.VERTICAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(12),dp(18),dp(12));row.setFocusable(true);row.setClickable(true);row.setDefaultFocusHighlightEnabled(false);row.setBackground(shape(0xe619282e,16));
            RecyclerView.LayoutParams size=new RecyclerView.LayoutParams(-1,dp(page.equals("home")?122:92));size.setMargins(dp(7),dp(7),dp(7),dp(7));row.setLayoutParams(size);
            TextView icon=text("",23,ACCENT),label=text("",20,TEXT),detail=text("",13,MUTED);label.setMaxLines(1);detail.setMaxLines(2);row.addView(icon);row.addView(label);row.addView(detail);
            row.setOnFocusChangeListener((v,on)->{motion.focus(v,on,true);if(on){v.setBackground(shape(0xf0254147,16));}else v.setBackground(shape(0xe619282e,16));});return new Holder(row,icon,label,detail);}
        public void onBindViewHolder(Holder h,int position){Tile item=items.get(position);h.icon.setText(item.icon);h.label.setText(tr(item.title));h.detail.setText(tr(item.detail));h.itemView.setContentDescription(item.title+" "+item.detail);h.itemView.setOnClickListener(v->{motion.press(v);item.run.run();});h.itemView.setScaleX(1);h.itemView.setScaleY(1);}
        public void onViewRecycled(Holder h){h.itemView.animate().cancel();h.itemView.setScaleX(1);h.itemView.setScaleY(1);h.itemView.setAlpha(1);h.itemView.setElevation(0);}
    }
    private List<Tile> tiles(){List<Tile> list=new ArrayList<>();
        switch(page){
        case "settings":
            list.add(new Tile("⌖","الموقع والمسجد",pref("zikra_city","اختر المدينة"),()->route("location")));
            list.add(new Tile("♫","الأذان والتنبيهات","الصوت لكل صلاة · الخلفية · الأذونات",()->route("adhan")));
            list.add(new Tile("◇","القبلة","اتجاه القبلة حسب الموقع",()->route("qibla")));
            list.add(new Tile("文","اللغة",pref("zikra_language","ar"),()->choose("اللغة",new String[]{"العربية","English","Deutsch"},index->{put("zikra_language",new String[]{"ar","en","de"}[index]);loadContent();render(false);} )));
            list.add(new Tile("⏻","البدء تلقائياً عند التشغيل",owner.getSharedPreferences("zikra_runtime",0).getBoolean("autostart",false)?"On":"Off",()->{SharedPreferences p=owner.getSharedPreferences("zikra_runtime",0);p.edit().putBoolean("autostart",!p.getBoolean("autostart",false)).apply();render(false);}));
            list.add(new Tile("↻","تحديث التطبيق","تنزيل التحديث وتثبيته",owner::startNativeUpdate));
            list.add(new Tile("☼","الخلفية","تغيير سطوع الخلفية",()->choose("سطوع الخلفية",new String[]{"هادئة","متوسطة","ساطعة"},i->{int value=new int[]{35,65,90}[i];put("tv_sky",String.valueOf(value));sky.setIntensity(value);} )));
            list.add(new Tile("⌫","إدارة بيانات التطبيق","مسح سجلات محددة أو إعادة الضبط",this::resetMenu));
            list.add(new Tile("♡","تبرّع لذِكرى","دعم استمرار التطبيق",()->message("دعم ذِكرى","لم تُضف وسيلة التبرع بعد.")));
            list.add(new Tile("ⓘ","حول ذِكرى","النسخة الأصلية للتلفزيون · التراخيص",()->message("ذِكرى للتلفزيون","ذِكرى للتلفزيون. تراخيص البرامج: CleanYourTV — Gathin 2026، MIT. خط Outfit: SIL OFL. بيانات الطقس: Open-Meteo؛ المدن والمساجد: OpenStreetMap contributors، ODbL 1.0؛ التلاوة: mp3quran.net.")));break;
        case "surahs":case "mushaf":
            if(page.equals("surahs")){list.add(new Tile("♫","اختيار القارئ",pref("tv_reader_name","اختر القارئ"),()->route("readers")));list.add(new Tile("▶","متابعة الاستماع",pref("tv_last_name","لا يوجد سجل بعد"),this::resumePlayback));}
            else list.add(new Tile("▤","متابعة القراءة","الصفحة "+number("tv_read_page",savedReadPage()),()->{readPage=number("tv_read_page",savedReadPage());route("reading");}));
            if(surahs!=null)for(int i=0;i<surahs.length();i++){JSONObject s=surahs.optJSONObject(i);if(s==null)continue;int n=s.optInt("number");list.add(new Tile(String.valueOf(n),s.optString("name"),s.optInt("numberOfAyahs")+" آية",()->{if(page.equals("mushaf")){readPage=s.optJSONArray("ayahs").optJSONObject(0).optInt("page",1);route("reading");}else playSurah(n);}));}
            else list.add(new Tile("…","جارٍ تجهيز الفهرس","المصحف محفوظ داخل التطبيق",()->{}));break;
        case "readers":
            list.add(new Tile("⌕","بحث عن قارئ",readerQuery.isEmpty()?"اكتب اسم القارئ":readerQuery,()->{EditText field=new EditText(owner);field.setSingleLine(true);field.setText(readerQuery);prompt("بحث عن قارئ","",field,new String[]{"بحث","كل القراء","رجوع"},i->{if(i==0){readerQuery=field.getText().toString();selections.put("readers",0);render(false);}else if(i==1){readerQuery="";render(false);}});}));
            if(readers.length()==0)list.add(new Tile("↻","تحميل القرّاء","يتطلب إنترنت",this::loadReaders));
            for(int i=0;i<readers.length();i++){JSONObject r=readers.optJSONObject(i);if(!NativeTvPolicy.matches(r.optString("name"),readerQuery))continue;list.add(new Tile("♫",r.optString("name"),r.optString("rewaya"),()->prompt("اخترت القارئ: "+r.optString("name"),"هل تريد استئناف القراءة؟",null,new String[]{"استئناف القراءة","الذهاب إلى فهرس السور","إلغاء"},choice->{if(choice==2)return;put("tv_reader",r.toString());put("tv_reader_name",r.optString("name"));playbackReader=r;if(choice==0){int surah=currentSurah,verse=currentVerse;try{JSONObject bookmark=new JSONObject(pref("quran_bookmark","{}"));if(player==null){surah=bookmark.optInt("surahNum",surah);verse=bookmark.optInt("ayahNum",verse);}}catch(Exception ignored){}playSurah(Math.max(1,Math.min(114,surah)),Math.max(1,verse));}else{page="surahs";render(false);}})));}break;
        case "prayer":
            for(int i=0;i<AdhanScheduler.KEYS.length;i++){String key=AdhanScheduler.KEYS[i];list.add(new Tile("◷",PRAYERS[i],prayerTime(key),()->adhanChoice(key)));}
            list.add(new Tile("↻","تحديث المواقيت",prayerLocationLabel(),()->{AdhanScheduler.refreshAsync(owner);notice("جارٍ تحديث مواقيت الصلاة…");}));break;
        case "location":
            list.add(new Tile("⌕",pref("zikra_city","اختيار المدينة"),"اختيار المدينة",this::searchCity));
            list.add(new Tile("⌖","موقعي الحالي","باستخدام موقع الجهاز بعد الإذن",this::locate));
            list.add(new Tile("⌂",mosqueName().isEmpty()?"اختيار المسجد":mosqueName(),"اختيار المسجد",this::mosques));
            list.add(new Tile("◷","طريقة حساب الصلاة",AdhanScheduler.configuration(owner).optInt("method",3)+"",()->choose("طريقة الحساب",new String[]{"رابطة العالم الإسلامي","أم القرى","مصر","أمريكا الشمالية","كراتشي","تركيا"},i->saveConfig("method",new int[]{3,4,5,2,1,13}[i]))));break;
        case "adhan":
            for(int i=0;i<AdhanScheduler.KEYS.length;i++){String key=AdhanScheduler.KEYS[i];JSONObject setting=AdhanScheduler.settings(owner,key);list.add(new Tile("♫",PRAYERS[i],setting!=null&&!setting.optBoolean("enabled",true)?"صامت":setting==null?"makkah":setting.optString("voice","makkah"),()->adhanChoice(key)));}
            list.add(new Tile("♫","معاينة صوت الأذان","توقّف عند الرجوع",()->choose("الصوت",new String[]{"مكة","المدينة","العفاسي","سوريا"},i->{},this::previewAdhan)));
            list.add(new Tile("◉","الأذان في الخلفية",AdhanScheduler.prefs(owner).getBoolean("background",true)?"On":"Off",()->{boolean on=!AdhanScheduler.prefs(owner).getBoolean("background",true);saveConfig("backgroundEnabled",on);AdhanScheduler.prefs(owner).edit().putBoolean("background",on).apply();AdhanScheduler.reschedule(owner);AdhanScheduler.scheduleJob(owner);AdhanService.stopIfDisabled(owner);render(false);}));
            list.add(new Tile("!","أذونات التنبيه","إشعارات ومواعيد دقيقة",()->{if(android.os.Build.VERSION.SDK_INT>=33&&owner.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)owner.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},4202);else if(android.os.Build.VERSION.SDK_INT>=31)owner.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+owner.getPackageName())));}));break;
        }
        return list;
    }
    private interface Choice{void picked(int index);}
    private void choose(String title,String[] values,Choice action){choose(title,values,action,null);}
    private void choose(String title,String[] values,Choice action,Choice preview){
        String[] labels=new String[values.length];for(int i=0;i<labels.length;i++)labels[i]=tr(values[i]);
        choicesDialog=NativeTvChoices.show(owner,uiFont,rtl(),tr(title),labels,action::picked,preview==null?null:preview::picked,()->{choicesDialog=null;stopPreview(true);});
    }
    private void previewAdhan(int index){
        if(index<0||index>3){if(previewPlayer!=null){previewPlayer.release();previewPlayer=null;}return;}
        if(!previewSession){previewSession=true;resumeAfterPreview=player!=null&&prepared&&!paused;if(resumeAfterPreview){try{player.pause();paused=true;}catch(Exception ignored){}}}
        if(previewPlayer!=null){previewPlayer.release();previewPlayer=null;}
        String voice=new String[]{"makkah","madinah","alafasy","syria"}[index];MediaPlayer preview=new MediaPlayer();previewPlayer=preview;
        preview.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        try{java.io.File local=new java.io.File(owner.getFilesDir(),"adhan/"+voice+".mp3");preview.setDataSource(local.length()>1024?local.getAbsolutePath():AdhanService.voiceUrl(voice));preview.setOnPreparedListener(p->{if(previewPlayer==p&&resumed)p.start();});preview.setOnErrorListener((p,w,e)->{if(previewPlayer==p){p.release();previewPlayer=null;}return true;});preview.prepareAsync();}catch(Exception ignored){preview.release();previewPlayer=null;}
    }
    private void stopPreview(boolean resume){
        if(previewPlayer!=null){previewPlayer.release();previewPlayer=null;}
        boolean restore=resumeAfterPreview;resumeAfterPreview=false;previewSession=false;
        if(resume&&restore&&resumed&&player!=null&&prepared&&AdhanService.activePrayer.isEmpty()){try{player.start();paused=false;refreshDashboard();}catch(Exception ignored){}}
    }
    private void prompt(String title,String body,EditText input,String[] labels,java.util.function.IntConsumer action){lastBackAt=0;NativeTvChoices.prompt(owner,uiFont,rtl(),title,body,input,labels,action);}
    private void message(String title,String value){prompt(title,value,null,new String[]{"موافق"},i->{});}
    private void notice(String value){status.setVisibility(View.GONE);}
    private void error(){if(!closed)message("تعذّر الاتصال","تحقق من الإنترنت وأعد المحاولة.");}
    private void body(String value,int size,Typeface face){ScrollView scroll=new ScrollView(owner);scroll.setClipToPadding(false);TextView label=text(value,size,TEXT);label.setTextIsSelectable(false);label.setPadding(dp(26),dp(18),dp(26),dp(18));label.setLineSpacing(dp(7),1.3f);if(face!=null)label.setTypeface(face);scroll.addView(label);scroll.setBackground(shape(0xd916272d,18));content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));scroll.setDefaultFocusHighlightEnabled(false);scroll.setFocusable(true);bodyScroll=scroll;scroll.requestFocus();}
    private void actions(String[] labels,Runnable[] actions){LinearLayout row=new LinearLayout(owner);pageActions=row;row.setGravity(Gravity.CENTER);for(int i=0;i<labels.length;i++){Button b=button(labels[i],actions[i]);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(6),dp(10),dp(6),0);row.addView(b,p);}content.addView(row);}
    private void addSideNavigation(){
        int leftDelta=NativeTvPolicy.horizontalStep(rtl(),true),rightDelta=NativeTvPolicy.horizontalStep(rtl(),false);
        sideLeft=sideArrow("←",leftDelta,leftDelta>0?"التالي":"السابق");sideRight=sideArrow("→",rightDelta,rightDelta>0?"التالي":"السابق");
        FrameLayout.LayoutParams left=new FrameLayout.LayoutParams(dp(52),dp(76),Gravity.LEFT|Gravity.CENTER_VERTICAL);left.leftMargin=dp(10);root.addView(sideLeft,left);
        FrameLayout.LayoutParams right=new FrameLayout.LayoutParams(dp(52),dp(76),Gravity.RIGHT|Gravity.CENTER_VERTICAL);right.rightMargin=dp(10);root.addView(sideRight,right);
    }
    private Button sideArrow(String glyph,int delta,String description){
        Button arrow=button(glyph,()->turnOrMemory(delta));arrow.setTextDirection(View.TEXT_DIRECTION_LTR);arrow.setTextSize(36);arrow.setPadding(0,0,0,dp(3));arrow.setAlpha(.38f);arrow.setBackground(shape(0x2679c9b2,26));arrow.setContentDescription(description);
        arrow.setOnFocusChangeListener((view,on)->{view.setAlpha(on?.95f:.38f);view.setBackground(shape(on?0x9979c9b2:0x2679c9b2,26));motion.focus(view,on,false);});return arrow;
    }
    private void removeSideNavigation(){if(sideLeft!=null){root.removeView(sideLeft);sideLeft=null;}if(sideRight!=null){root.removeView(sideRight);sideRight=null;}}
    private void turnOrMemory(int delta){if(page.equals("reading")){turn(delta);return;}if(page.equals("memory")){memoryIndex+=delta;nextMemoryAt=android.os.SystemClock.elapsedRealtime()+15000;render(delta<0);}}
    private void quranToolbar(boolean reading){
        LinearLayout tools=new LinearLayout(owner);tools.setGravity(Gravity.CENTER_VERTICAL);tools.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView label=text("حجم الخط",14,MUTED);tools.addView(label,new LinearLayout.LayoutParams(dp(86),dp(36)));
        Button smaller=button("−",()->styleQuran(-2,false));Button larger=button("+",()->styleQuran(2,false));Button background=button("فاتح / داكن",()->styleQuran(0,true));
        tools.addView(smaller,new LinearLayout.LayoutParams(dp(46),dp(34)));tools.addView(larger,new LinearLayout.LayoutParams(dp(46),dp(34)));tools.addView(background,new LinearLayout.LayoutParams(dp(132),dp(34)));
        View space=new View(owner);tools.addView(space,new LinearLayout.LayoutParams(0,1,1));
        Button index=button("فهرس السور",()->route(reading?"mushaf":"surahs"));tools.addView(index,new LinearLayout.LayoutParams(dp(128),dp(34)));
        if(reading){Button previous=button("◀",()->turn(-1)),next=button("▶",()->turn(1));previous.setContentDescription("الصفحة السابقة");next.setContentDescription("الصفحة التالية");tools.addView(previous,new LinearLayout.LayoutParams(dp(54),dp(34)));tools.addView(next,new LinearLayout.LayoutParams(dp(54),dp(34)));}
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,dp(40));size.bottomMargin=dp(6);content.addView(tools,size);
    }
    private void styleQuran(int delta,boolean toggle){
        int size=Math.max(22,Math.min(48,number("tv_quran_font",30)+delta));put("tv_quran_font",String.valueOf(size));
        if(toggle)put("tv_quran_light",String.valueOf(!pref("tv_quran_light","false").equals("true")));
        if(quranCard!=null)quranCard.style(size,pref("tv_quran_light","false").equals("true"));
    }
    private void createQuranCard(){
        quranCard=new NativeQuranCard(owner,uiFont,quranBook,number("tv_quran_font",30),pref("tv_quran_light","false").equals("true"));content.addView(quranCard,new LinearLayout.LayoutParams(-1,0,1));bodyScroll=quranCard.scroller();NativeQuranCard card=quranCard;bodyScroll.setOnFocusChangeListener((view,on)->motion.focus(card,on,false));
    }
    private void playerPage(){
        quranToolbar(false);createQuranCard();drawPlayerText();
        actions(new String[]{"سورة −","آية −","تشغيل / إيقاف","آية +","سورة +"},new Runnable[]{()->playSurah(Math.max(1,currentSurah-1)),()->seekVerse(-1),this::togglePlayback,()->seekVerse(1),()->playSurah(Math.min(114,currentSurah+1))});
    }
    private void drawPlayerText(){
        if(quranCard==null||quranBook==null)return;
        boolean timed=player!=null&&prepared&&verseStarts.length>0;
        if(page.equals("reading")){quranCard.show(readPage,0,0,false,"");return;}
        int activePage=quranBook.pageOf(currentSurah,currentVerse);
        quranCard.show(activePage,currentSurah,currentVerse,timed,playbackReader.optString("name",pref("tv_reader_name","")));
    }

    private void loadTimings(JSONObject reader,int surah,long sequence){
        if(!reader.optBoolean("timing",false))return;
        io.execute(()->{try{JSONArray rows=new JSONArray(requestText("https://mp3quran.net/api/v3/ayat_timing?surah="+surah+"&read="+reader.optInt("id")));List<long[]> valid=new ArrayList<>();for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null||r.isNull("start_time"))continue;long start=r.optLong("start_time",-1);int verse=r.optInt("ayah",0);if(start>=0&&verse>0&&verse<=286)valid.add(new long[]{start,verse});}java.util.Collections.sort(valid,(a,b)->Long.compare(a[0],b[0]));main.post(()->{if(closed||sequence!=playbackSequence)return;verseStarts=new long[valid.size()];verseNumbers=new int[valid.size()];for(int i=0;i<valid.size();i++){verseStarts[i]=valid.get(i)[0];verseNumbers[i]=(int)valid.get(i)[1];}renderedVerse=-1;if(page.equals("player")||page.equals("reading"))drawPlayerText();if(verseStarts.length==0&&pendingVerse>0){pendingVerse=0;message("التنقل بين الآيات","لا يتوفر توقيت للآيات في هذا التسجيل.");}applyVerseSeek();refreshDashboard();});}catch(Exception ignored){main.post(()->{if(sequence==playbackSequence&&pendingVerse>0){pendingVerse=0;message("التنقل بين الآيات","تعذّر تحميل توقيت الآيات لهذا التسجيل. أعد المحاولة أو اختر تسجيلًا آخر من الفهرس.");}});}});
    }
    private void reading(){
        quranToolbar(true);createQuranCard();drawPlayerText();rememberReadPage();
        ScrollView sheet=bodyScroll;sheet.post(()->{if(sheet==bodyScroll)sheet.requestFocus();});
    }
    private void rememberReadPage(){
        put("tv_read_page",String.valueOf(readPage));try{put("zikra_mushaf_page_v1",new JSONObject().put("page",readPage).put("scrollTop",0).toString());}catch(Exception ignored){}
    }
    private void turn(int delta){
        if(delta>0&&surahs!=null)for(int i=0;i<surahs.length();i++){JSONObject surah=surahs.optJSONObject(i);JSONArray verses=surah.optJSONArray("ayahs");if(verses!=null&&verses.length()>0&&verses.optJSONObject(verses.length()-1).optInt("page")==readPage)markCompleted("zikra_mushaf_read_surahs_v1",surah.optInt("number"));}
        readPage=NativeTvPolicy.page(readPage+delta);render(delta<0);
    }
    private void markCompleted(String key,int number){try{JSONArray values=new JSONArray(pref(key,"[]"));for(int i=0;i<values.length();i++)if(values.optInt(i)==number)return;values.put(number);put(key,values.toString());}catch(Exception ignored){}}

    private int savedReadPage(){try{return NativeTvPolicy.page(new JSONObject(pref("zikra_mushaf_page_v1","{}")).optInt("page",1));}catch(Exception ignored){return 1;}}
    private void memory(){
        JSONObject item=memories==null?null:memories.optJSONObject(Math.floorMod(memoryIndex,Math.max(1,memories.length())));
        memoryBody(item==null?"جارٍ تحميل الأذكار المحفوظة…":item.optString("title")+"\n\n"+item.optString("text")+"\n\n"+item.optString("source")+"\n\n"+item.optString("reflection")+"\n"+item.optString("lesson"));
        actions(new String[]{"السابق","الرئيسية","التالي"},new Runnable[]{()->{memoryIndex--;render(true);},this::back,()->{memoryIndex++;render(false);}});
    }
    private void memoryBody(String value){FrameLayout card=new FrameLayout(owner);card.setBackground(shape(0xd916272d,18));card.setClipToPadding(true);card.setClipChildren(true);TextView label=text(value,18,TEXT);label.setGravity(Gravity.CENTER);label.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);label.setPadding(dp(28),dp(22),dp(28),dp(22));label.setLineSpacing(dp(3),1.05f);label.setMaxLines(80);label.setAutoSizeTextTypeUniformWithConfiguration(9,18,1,android.util.TypedValue.COMPLEX_UNIT_SP);card.addView(label,new FrameLayout.LayoutParams(-1,-1));content.addView(card,new LinearLayout.LayoutParams(-1,0,1));bodyScroll=null;}
    private void tasbeeh(){prompt("التسبيح · "+number("zikra_tasbeeh_count",0),"",null,new String[]{"+1","تصفير العداد","رجوع"},i->{if(i==0)put("zikra_tasbeeh_count",String.valueOf(number("zikra_tasbeeh_count",0)+1));else if(i==1)put("zikra_tasbeeh_count","0");refreshDashboard();});}
    private String nextPrayer(){try{JSONArray rows=new JSONArray(AdhanScheduler.prefs(owner).getString("schedule","[]"));long now=AdhanScheduler.now(owner),next=Long.MAX_VALUE;String key="";for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);long t=r.optLong("epoch");if(t>now&&t<next){next=t;key=r.optString("key");}}if(!key.isEmpty())return prayerName(key)+"  "+format("HH:mm",next);}catch(Exception ignored){}return "حدد الموقع لإظهار المواقيت";}
    private String prayerName(String key){for(int i=0;i<AdhanScheduler.KEYS.length;i++)if(key.equals(AdhanScheduler.KEYS[i]))return PRAYERS[i];return key;}
    private String prayerTime(String key){try{JSONArray rows=new JSONArray(AdhanScheduler.prefs(owner).getString("schedule","[]"));long today=AdhanScheduler.now(owner);String date=format("yyyy-MM-dd",today);for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(key.equals(r.optString("key"))&&date.equals(r.optString("date")))return format("HH:mm",r.optLong("epoch"));}}catch(Exception ignored){}return "—";}
    private void adhanChoice(String key){choose("أذان "+prayerName(key),new String[]{"مكة","المدينة","العفاسي","سوريا","صامت"},i->{try{JSONObject settings=new JSONObject(AdhanScheduler.prefs(owner).getString("settings","{}"));settings.put(key,new JSONObject().put("enabled",i!=4).put("voice",new String[]{"makkah","madinah","alafasy","syria","makkah"}[i]));saveSchedule(AdhanScheduler.configuration(owner),settings);render(false);}catch(Exception e){error();}},this::previewAdhan);}
    private void saveSchedule(JSONObject config,JSONObject settings)throws Exception{for(String key:AdhanScheduler.KEYS)if(!settings.has(key))settings.put(key,new JSONObject().put("enabled",true).put("voice","makkah"));AdhanScheduler.update(owner,new JSONObject().put("config",config).put("settings",settings).toString());}
    private void saveConfig(String key,Object value){try{JSONObject c=AdhanScheduler.configuration(owner);c.put(key,value);saveSchedule(c,new JSONObject(AdhanScheduler.prefs(owner).getString("settings","{}")));}catch(Exception e){error();}}
    private void searchCity(){EditText input=new EditText(owner);input.setText(pref("zikra_city",""));input.setSingleLine(true);input.setHint("اكتب اسم المدينة");prompt("اختيار المدينة","",input,new String[]{"بحث","رجوع"},i->{if(i==0)findCities(input.getText().toString());});}
    private void findCities(String query){if(query.trim().length()<2)return;notice("جارٍ البحث عن المدينة…");io.execute(()->{try{JSONArray results=request("https://geocoding-api.open-meteo.com/v1/search?name="+URLEncoder.encode(query,"UTF-8")+"&count=10&language="+pref("zikra_language","ar")+"&format=json").optJSONArray("results");main.post(()->{if(closed)return;if(results==null||results.length()==0){notice("لا توجد نتائج. جرّب اسمًا آخر.");return;}String[] names=new String[results.length()];for(int i=0;i<names.length;i++){JSONObject r=results.optJSONObject(i);names[i]=r.optString("name")+" · "+r.optString("admin1")+" · "+r.optString("country");}choose("نتائج البحث",names,i->{JSONObject r=results.optJSONObject(i);location(r.optDouble("latitude"),r.optDouble("longitude"),r.optString("name"),r.optString("timezone",TimeZone.getDefault().getID()),"");});});}catch(Exception e){main.post(this::error);}});}
    private String prayerLocationLabel(){
        String city=pref("zikra_city","اختر المدينة من الإعدادات");String mosque=pref("tv_mosque_name","");
        if(mosque.isEmpty())try{JSONObject old=new JSONObject(pref("zikra_mosque","{}"));mosque=old.optString("title","");}catch(Exception ignored){}
        if(mosque.isEmpty()||mosque.equals(city))return city;return city+" - "+mosque;
    }
    private String mosqueName(){
        String name=pref("tv_mosque_name","");if(!name.isEmpty())return name;
        try{JSONObject old=new JSONObject(pref("zikra_mosque","{}"));if(!old.optString("title").isEmpty())return old.optString("title");}catch(Exception ignored){}
        return AdhanScheduler.configuration(owner).optString("mosque").isEmpty()?"":pref("zikra_city","");
    }
    private void location(double lat,double lon,String city,String zone,String mosque){
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)return;
        try{JSONObject config=AdhanScheduler.configuration(owner);config.put("lat",lat).put("lon",lon).put("zone",zone).put("mosque",mosque).put("kind",mosque.isEmpty()?"city":"mosque");
            put("zikra_lat",String.valueOf(lat));put("zikra_lon",String.valueOf(lon));put("zikra_timezone",zone);
            if(mosque.isEmpty()){put("zikra_city",city);data.edit().remove("tv_mosque_name").remove("zikra_mosque").apply();}else put("tv_mosque_name",city);
            saveSchedule(config,new JSONObject(AdhanScheduler.prefs(owner).getString("settings","{}")));weather=null;data.edit().remove("tv_weather").apply();render(false);loadWeather(true);
        }catch(Exception e){error();}
    }
    private void resolveLocation(Location fix){
        final double lat=fix.getLatitude(),lon=fix.getLongitude();
        io.execute(()->{String city="";
            try{JSONObject result=request("https://photon.komoot.io/reverse?lat="+lat+"&lon="+lon+"&lang="+(pref("zikra_language","ar").equals("de")?"de":"en")+"&limit=1");JSONObject properties=result.getJSONArray("features").getJSONObject(0).getJSONObject("properties");for(String key:new String[]{"city","town","village","municipality","name"})if(!properties.optString(key).isEmpty()){city=properties.getString(key);break;}}catch(Exception ignored){}
            if(city.isEmpty())try{android.location.Geocoder geocoder=new android.location.Geocoder(owner,Locale.forLanguageTag(pref("zikra_language","ar")));java.util.List<android.location.Address> addresses=geocoder.getFromLocation(lat,lon,1);if(addresses!=null&&!addresses.isEmpty()){android.location.Address address=addresses.get(0);city=address.getLocality();if(city==null||city.isEmpty())city=address.getSubAdminArea();}}catch(Exception ignored){}
            final String found=city==null||city.isEmpty()?String.format(Locale.US,"%.3f, %.3f",lat,lon):city;
            main.post(()->{if(!closed)location(lat,lon,found,TimeZone.getDefault().getID(),"");});
        });
    }
    private void locate(){if(owner.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&owner.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){owner.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},4201);return;}LocationManager manager=owner.getSystemService(LocationManager.class);if(manager==null){notice("استخدم البحث عن المدينة على هذا الجهاز.");return;}try{for(String provider:manager.getProviders(true)){Location last=manager.getLastKnownLocation(provider);if(last!=null&&System.currentTimeMillis()-last.getTime()<3600000){resolveLocation(last);return;}}gps=new LocationListener(){public void onLocationChanged(Location l){manager.removeUpdates(this);gps=null;resolveLocation(l);}public void onProviderEnabled(String p){}public void onProviderDisabled(String p){}public void onStatusChanged(String p,int s,Bundle b){}};String provider=manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)?LocationManager.NETWORK_PROVIDER:LocationManager.GPS_PROVIDER;manager.requestLocationUpdates(provider,0,0,gps,Looper.getMainLooper());notice("بانتظار موقع الجهاز… يمكنك اختيار المدينة يدويًا.");main.postDelayed(()->{if(gps!=null){try{manager.removeUpdates(gps);}catch(Exception ignored){}gps=null;notice("لم يصل موقع الجهاز. اختر المدينة يدويًا.");}},15000);}catch(Exception e){notice("هذا الجهاز لا يوفر الموقع. اختر المدينة يدويًا.");}}
    void permissionResult(int requestCode,int[] grants){if(requestCode==4201){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)locate();else notice("لم يُمنح إذن الموقع. اختر المدينة يدويًا.");}}
    private void mosques(){
        JSONObject config=AdhanScheduler.configuration(owner);if(!config.has("lat")){searchCity();return;}
        final long token=requestId;notice("جارٍ البحث عن المساجد…");
        io.execute(()->{try{
            double lat=config.optDouble("lat"),lon=config.optDouble("lon");JSONArray found=new JSONArray();
            try{Object response=new JSONTokener(requestText("https://pray.zone/api/mosques/nearby?latitude="+lat+"&longitude="+lon+"&maxDistance=25000")).nextValue();
                if(response instanceof JSONArray){JSONArray source=(JSONArray)response;for(int i=0;i<source.length();i++){JSONObject item=source.optJSONObject(i);if(item==null)continue;JSONArray coords=item.optJSONArray("coordinates");if(coords==null||coords.length()<2)continue;found.put(new JSONObject().put("id",item.optString("slug")).put("title",item.optString("title")).put("lat",coords.optDouble(1)).put("lon",coords.optDouble(0)));}}
            }catch(Exception ignored){}
            if(found.length()==0){
                String query="[out:json][timeout:15];nwr(around:25000,"+lat+","+lon+")[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"];out center tags;";
                JSONArray elements=request("https://overpass-api.de/api/interpreter?data="+URLEncoder.encode(query,"UTF-8")).getJSONArray("elements");
                for(int i=0;i<elements.length()&&found.length()<60;i++){JSONObject item=elements.getJSONObject(i),center=item.optJSONObject("center"),tags=item.optJSONObject("tags");if(tags==null)continue;double aLat=item.has("lat")?item.optDouble("lat"):center==null?Double.NaN:center.optDouble("lat"),aLon=item.has("lon")?item.optDouble("lon"):center==null?Double.NaN:center.optDouble("lon");if(!Double.isFinite(aLat)||!Double.isFinite(aLon))continue;
                    found.put(new JSONObject().put("id","osm-"+item.optString("type")+"-"+item.optLong("id")).put("title",tags.optString("name:"+pref("zikra_language","ar"),tags.optString("name","مسجد"))).put("lat",aLat).put("lon",aLon));}
            }
            final JSONArray results=found;main.post(()->{if(closed||token!=requestId||!page.equals("location"))return;if(results.length()==0){notice("لم نجد مساجد قريبة. يمكنك استخدام مواقيت المدينة.");return;}String[] names=new String[results.length()];for(int i=0;i<names.length;i++)names[i]=results.optJSONObject(i).optString("title");
                choose("اختيار المسجد",names,index->{JSONObject item=results.optJSONObject(index);String id=item.optString("id");if(id.equals("erfurt_erfurter-moschee")){location(item.optDouble("lat"),item.optDouble("lon"),names[index],"Europe/Berlin",id);return;}
                    prompt(names[index],"هذا إدراج في دليل المساجد، ولا يوفّر تقويمًا رسميًا موثوقًا. استخدام مواقيت محسوبة لإحداثياته؟",null,new String[]{"استخدام المواقيت المحسوبة","رجوع"},i->{if(i==0)location(item.optDouble("lat"),item.optDouble("lon"),names[index],config.optString("zone",TimeZone.getDefault().getID()),id);});});});
        }catch(Exception e){main.post(()->{if(!closed&&token==requestId)error();});}});
    }
    private void qibla(){JSONObject c=AdhanScheduler.configuration(owner);if(!c.has("lat")){body("اختر المدينة أولًا من الإعدادات.",24,null);return;}double angle=NativeTvPolicy.qibla(c.optDouble("lat"),c.optDouble("lon"));body("القبلة\n\n"+String.format(Locale.US,"%.1f°",angle)+"\n\nمن الشمال الجغرافي باتجاه عقارب الساعة.\nالشاشة ثابتة؛ لا يُفترض أن أعلاها يشير إلى الشمال.",28,null);}
    private void loadWeather(){loadWeather(false);}
    private void loadWeather(boolean quiet){
        JSONObject config=AdhanScheduler.configuration(owner);
        if(!config.has("lat")){if(!quiet)notice("اختر المدينة لإظهار الطقس.");return;}
        if(weatherLoading)return;weatherLoading=true;
        final String identity=config.optString("lat")+"|"+config.optString("lon");
        if(!quiet)notice("جارٍ تحميل الطقس…");
        io.execute(()->{try{
            JSONObject result=request("https://api.open-meteo.com/v1/forecast?latitude="+config.optDouble("lat")+"&longitude="+config.optDouble("lon")+"&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code,sunrise,sunset&hourly=temperature_2m,precipitation_probability,wind_speed_10m&timezone=auto&forecast_days=10");
            main.post(()->{weatherLoading=false;if(closed)return;JSONObject current=AdhanScheduler.configuration(owner);if(!identity.equals(current.optString("lat")+"|"+current.optString("lon")))return;
                weather=result;weatherFetched=System.currentTimeMillis();put("tv_weather",result.toString());refreshDashboard();if(page.equals("weather"))render(false);if(!quiet)status.setVisibility(View.GONE);
            });
        }catch(Exception e){main.post(()->{weatherLoading=false;if(!quiet)error();});}});
    }
    private void homeAction(String action){
        switch(action){
            case "weather":route("weather");loadWeather();break;
            case "prayer":route("prayer");break;
            case "quran":route("surahs");break;
            case "mushaf":route("mushaf");break;
            case "readers":route("readers");break;
            case "memory":route("memory");break;
            case "play":togglePlayback();break;
            case "verse-previous":seekVerse(-1);break;
            case "verse-next":seekVerse(1);break;
            case "previous":playSurah(Math.max(1,currentSurah-1));break;
            case "next":playSurah(Math.min(114,currentSurah+1));break;
            case "tasbeeh-add":put("zikra_tasbeeh_count",String.valueOf(number("zikra_tasbeeh_count",0)+1));refreshDashboard();break;
            case "tasbeeh-reset":prompt("تصفير عداد التسبيح؟","",null,new String[]{"تصفير","رجوع"},index->{if(index==0){put("zikra_tasbeeh_count","0");refreshDashboard();}});break;
        }
    }
    private void refreshDashboard(){
        if(homeDashboard==null||!page.equals("home")||closed)return;
        long now=AdhanScheduler.now(owner);String schedule=AdhanScheduler.prefs(owner).getString("schedule","[]");
        if(now/60000!=homeMinute||!schedule.equals(homeSchedule)){
            homeMinute=now/60000;homeSchedule=schedule;String[] values={"—","—","—","—","—"};String today=format("yyyy-MM-dd",now),nextName="";long next=Long.MAX_VALUE;int nextIndex=-1;
            try{JSONArray rows=new JSONArray(schedule);for(int n=0;n<rows.length();n++){JSONObject r=rows.getJSONObject(n);String key=r.optString("key");long epoch=r.optLong("epoch");for(int i=0;i<AdhanScheduler.KEYS.length;i++)if(key.equals(AdhanScheduler.KEYS[i])){if(today.equals(r.optString("date")))values[i]=format("HH:mm",epoch);if(epoch>now&&epoch<next){next=epoch;nextName=PRAYERS[i];nextIndex=i;}break;}}}catch(Exception ignored){}
            homeDashboard.prayer(values,nextIndex,nextName,next==Long.MAX_VALUE?0:next);
        }
        homeDashboard.clock(now);homeDashboard.tasbeeh(pref("zikra_tasbeeh_count","0"));
        if(weather==null)try{weather=new JSONObject(pref("tv_weather","{}"));}catch(Exception ignored){}
        JSONObject current=weather==null?null:weather.optJSONObject("current");
        String temp="— °C",detail="اختر الموقع لإظهار الطقس";
        if(current!=null){temp=String.format(Locale.US,"%.0f °C",current.optDouble("temperature_2m"));detail=weatherName(current.optInt("weather_code"))+"  ·  "+current.optInt("relative_humidity_2m")+"% رطوبة  ·  "+String.format(Locale.US,"%.0f km/h",current.optDouble("wind_speed_10m"));}
        homeDashboard.weather(pref("zikra_city","حدد الموقع"),temp,detail);
        if(memories!=null&&memories.length()>0){JSONObject item=memories.optJSONObject(Math.floorMod(memoryIndex,memories.length()));homeDashboard.remembrance(item.optString("text"));}
        refreshHomeQuran();
    }
    private void refreshHomeQuran(){
        if(homeDashboard==null||!page.equals("home")||closed)return;
        int displaySurah=currentSurah,displayVerse=currentVerse;String name=playingName.isEmpty()?pref("tv_last_name",""):playingName;
        if(player==null)try{JSONObject saved=new JSONObject(pref("quran_bookmark","{}"));displaySurah=saved.optInt("surahNum",1);displayVerse=saved.optInt("ayahNum",1);}catch(Exception ignored){}
        int displayPage=1;String ayah="";if(surahs!=null&&displaySurah>=1&&displaySurah<=surahs.length()){JSONObject item=surahs.optJSONObject(displaySurah-1);if(name.isEmpty())name=item.optString("name");JSONArray verses=item.optJSONArray("ayahs");if(verses!=null&&displayVerse>=1&&displayVerse<=verses.length()){JSONObject selectedAyah=verses.optJSONObject(displayVerse-1);ayah=selectedAyah.optString("text");displayPage=selectedAyah.optInt("page",1);}}
        homeDashboard.player(name,prepared?playbackReader.optString("name",pref("tv_reader_name","")):pref("tv_reader_name",""),ayah,prepared&&!paused,displayPage,displayVerse);
    }
    private String weatherName(int code){if(code==0)return "سماء صافية";if(code<=3)return "غائم جزئيًا";if(code<=48)return "ضباب";if(code>=95)return "عواصف رعدية";if(code>=71&&code<=77||code==85||code==86)return "ثلوج";return "أمطار";}
    private void weather(){
        weatherDay=0;
        if(weather==null)try{weather=new JSONObject(pref("tv_weather","{}"));}catch(Exception ignored){}
        weatherPage=new NativeWeatherView(owner,uiFont,motion,weather,pref("zikra_city",""),rtl(),weatherDay,value->weatherDay=value,this::loadWeather,()->route("location"));content.addView(weatherPage,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void loadContent(){final String language=pref("zikra_language","ar");io.execute(()->{try{JSONObject translated=new JSONObject(asset("assets/data/native-translations.json"));String source=asset("assets/data/quran-text.js");JSONObject q=new JSONObject(source.substring(source.indexOf('{'),source.lastIndexOf('}')+1));JSONArray all=q.getJSONArray("surahs");NativeQuranBook book=new NativeQuranBook(all);String memory=asset("assets/data/remembrance-"+language+".js");JSONArray items=new JSONArray(memory.substring(memory.indexOf('['),memory.lastIndexOf(']')+1));main.post(()->{if(closed||!language.equals(pref("zikra_language","ar")))return;translations=translated;surahs=all;quranBook=book;memories=items;if(page.equals("mushaf")||page.equals("surahs")||page.equals("reading")||page.equals("player")||page.equals("memory")||!language.equals("ar"))render(false);refreshDashboard();});}catch(Exception e){main.post(()->notice("تعذّر قراءة المحتوى المحلي."));}});}
    private String asset(String path)throws Exception{try(InputStream input=owner.getAssets().open(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=input.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    private String requestText(String address)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","Zikra-Native-TV");try{if(c.getResponseCode()!=200)throw new java.io.IOException();try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(closed||Thread.currentThread().isInterrupted())throw new java.io.IOException();if(out.size()+n>4194304)throw new java.io.IOException();out.write(b,0,n);}return out.toString("UTF-8");}}finally{c.disconnect();}}
    private JSONObject request(String address)throws Exception{return new JSONObject(requestText(address));}
    private void loadReaders(){try{readers=new JSONArray(pref("tv_readers","[]"));}catch(Exception ignored){}io.execute(()->{try{JSONArray catalog=request("https://mp3quran.net/api/v3/reciters?language=ar").getJSONArray("reciters"),result=new JSONArray();for(int i=0;i<catalog.length();i++){JSONObject reader=catalog.getJSONObject(i);JSONArray folders=reader.optJSONArray("moshaf");if(folders==null)continue;for(int j=0;j<folders.length();j++){JSONObject folder=folders.getJSONObject(j);String server=folder.optString("server");if(!server.startsWith("https://"))continue;result.put(new JSONObject().put("id",folder.optInt("id")).put("name",reader.optString("name")).put("rewaya",folder.optString("name")).put("server",server).put("surah_list",folder.optString("surah_list")));}}try{JSONArray timed=new JSONArray(requestText("https://mp3quran.net/api/v3/ayat_timing/reads"));for(int t=0;t<timed.length();t++){JSONObject source=timed.getJSONObject(t);boolean found=false;for(int r=0;r<result.length();r++){JSONObject row=result.getJSONObject(r);if(row.optInt("id")==source.optInt("id")){row.put("timing",true);if(source.optString("folder_url").startsWith("https://"))row.put("server",source.getString("folder_url"));found=true;break;}}if(!found&&source.optString("folder_url").startsWith("https://"))result.put(new JSONObject().put("id",source.optInt("id")).put("name",source.optString("name")).put("rewaya",source.optString("rewaya")).put("server",source.optString("folder_url")).put("timing",true));}}catch(Exception ignored){}
        main.post(()->{if(closed)return;readers=result;put("tv_readers",result.toString());importPlaybackBookmark();if(page.equals("readers"))render(false);refreshDashboard();});}catch(Exception e){if(readers.length()==0)main.post(()->{if(page.equals("readers"))error();});}});}
    private void importPlaybackBookmark(){
        if(!pref("tv_last_url","").isEmpty())return;
        try{JSONObject old=new JSONObject(pref("quran_bookmark","{}"));int id=old.optInt("readerId",-1),surah=old.optInt("surahNum",0);
            if(surah<1||surah>114)return;
            for(int i=0;i<readers.length();i++){JSONObject reader=readers.getJSONObject(i);if(reader.optInt("id")!=id)continue;
                put("tv_reader",reader.toString());put("tv_reader_name",reader.optString("name"));
                put("tv_last_url",NativeTvPolicy.audioUrl(reader.getString("server"),surah));put("tv_last_name",old.optString("surahName","القرآن الكريم"));
                put("tv_resume_ms",String.valueOf(Math.max(0,(int)(old.optDouble("time",0)*1000))));currentSurah=surah;break;
            }
        }catch(Exception ignored){}
    }
    private int nextAvailableSurah(int completed,JSONObject reader){if(surahs==null)return 0;String list=reader==null?"":reader.optString("surah_list","");for(int n=completed+1;n<=Math.min(114,surahs.length());n++){if(list.isEmpty())return n;boolean found=false;for(String value:list.split(",")){try{if(Integer.parseInt(value.trim())==n){found=true;break;}}catch(Exception ignored){}}if(found)return n;}return 0;}
    private void playSurah(int number){playSurah(number,1);}
    private void playSurah(int number,int startVerse){try{JSONObject reader=new JSONObject(pref("tv_reader","{}"));if(!reader.has("server")){notice("اختر القارئ أولًا.");route("readers");return;}String available=","+reader.optString("surah_list")+",";if(!available.equals(",,")&&!available.contains(","+number+",")){notice("هذه السورة غير متوفرة لهذا القارئ.");return;}savePlayback();String title=surahs.getJSONObject(number-1).optString("name");String url=NativeTvPolicy.audioUrl(reader.getString("server"),number);playUrl(url,title,true,false);playbackReader=reader;currentSurah=number;currentVerse=Math.max(1,startVerse);if(currentVerse>1)pendingVerse=currentVerse;verseStarts=new long[0];verseNumbers=new int[0];if(page.equals("home"))refreshDashboard();else if(page.equals("reading"))drawPlayerText();else if(!page.equals("player"))route("player");loadTimings(reader,number,playbackSequence);}catch(Exception e){error();}}private void playUrl(String url,String title,boolean remember,boolean resumeFromBookmark){if(!url.startsWith("https://")){notice("رابط الصوت غير صالح.");return;}savePlayback();releasePlayer();playbackSequence++;pendingVerse=0;seekingVerse=false;playingUrl=url;playingName=title;prepared=false;paused=true;player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());final MediaPlayer active=player;try{active.setDataSource(url);active.setOnPreparedListener(p->{if(player!=active)return;prepared=true;int offset=number("tv_resume_ms",0);if(pendingVerse==0&&resumeFromBookmark&&url.equals(pref("tv_last_url",""))&&offset>0)p.seekTo(offset);p.start();paused=false;applyVerseSeek();main.removeCallbacks(audioTick);main.post(audioTick);if(remember){put("tv_last_url",url);put("tv_last_name",title);}notice("يعمل الآن: "+title);if(page.equals("player")||page.equals("reading"))drawPlayerText();refreshDashboard();});active.setOnSeekCompleteListener(p->{if(player==p&&seekingVerse){currentVerse=pendingVerse;pendingVerse=0;seekingVerse=false;renderedVerse=-1;if(page.equals("player")||page.equals("reading"))drawPlayerText();refreshDashboard();savePlayback();}});active.setOnCompletionListener(p->{if(player!=active)return;paused=true;put("tv_resume_ms","0");if(remember)markCompleted("played_surahs",currentSurah);int next=nextAvailableSurah(currentSurah,playbackReader);if(next>0){playSurah(next,1);}else{notice("اكتملت التلاوة");refreshDashboard();}});active.setOnErrorListener((p,w,e)->{if(player==active){releasePlayer();notice("تعذّر تشغيل الصوت. تحقق من الإنترنت وأعد المحاولة.");}return true;});active.prepareAsync();notice("جارٍ تجهيز "+title+"…");}catch(Exception e){releasePlayer();error();}}
    private void togglePlayback(){if(player==null){resumePlayback();return;}if(!prepared)return;try{if(player.isPlaying()){player.pause();paused=true;}else{player.start();paused=false;}notice(paused?"تم إيقاف التلاوة مؤقتًا":"يعمل الآن: "+playingName);refreshDashboard();}catch(Exception e){releasePlayer();}}
    private void resumePlayback(){savePlayback();releasePlayer();verseStarts=new long[0];verseNumbers=new int[0];String url=pref("tv_last_url","");if(url.isEmpty()){route("surahs");return;}try{JSONObject bookmark=new JSONObject(pref("quran_bookmark","{}"));currentSurah=bookmark.optInt("surahNum",currentSurah);currentVerse=bookmark.optInt("ayahNum",1);JSONObject reader=new JSONObject(pref("tv_reader","{}"));for(int i=0;i<readers.length();i++)if(readers.optJSONObject(i).optInt("id")==bookmark.optInt("readerId")){reader=readers.optJSONObject(i);break;}playbackReader=reader;playUrl(url,pref("tv_last_name","القرآن الكريم"),true,true);if(!page.equals("player")&&!page.equals("home")&&!page.equals("reading"))route("player");refreshDashboard();loadTimings(reader,currentSurah,playbackSequence);}catch(Exception e){playUrl(url,pref("tv_last_name","القرآن الكريم"),true,true);}}
    private void savePlayback(){if(player==null||!prepared||!playingUrl.equals(pref("tv_last_url","")))return;try{int position=player.getCurrentPosition();put("tv_resume_ms",String.valueOf(position));
        JSONObject reader=playbackReader;put("quran_bookmark",new JSONObject().put("surahNum",currentSurah).put("readerId",reader.optInt("id")).put("ayahNum",currentVerse).put("time",position/1000d).put("surahName",playingName).put("readerName",pref("tv_reader_name","")).put("updated",System.currentTimeMillis()).toString());}catch(Exception ignored){}}
    private void releasePlayer(){main.removeCallbacks(audioTick);prepared=false;paused=true;if(player!=null){player.release();player=null;}}
    private void seekVerse(int delta){
        if(surahs==null)return;
        if(player==null)resumePlayback();
        if(player==null)return;
        JSONObject surah=surahs.optJSONObject(Math.max(0,Math.min(surahs.length()-1,currentSurah-1)));JSONArray verses=surah==null?null:surah.optJSONArray("ayahs");if(verses==null)return;
        pendingVerse=NativeTvPolicy.stepVerse(pendingVerse>0?pendingVerse:currentVerse,delta,verses.length());seekingVerse=false;
        if(verseStarts.length==0){if(!playbackReader.optBoolean("timing",false)){pendingVerse=0;message("التنقل بين الآيات","هذا التسجيل لا يوفّر توقيت الآيات. اختر تسجيلًا آخر من الفهرس للتنقل بدقة.");}return;}
        applyVerseSeek();
    }
    private void applyVerseSeek(){
        if(pendingVerse<=0||seekingVerse||!prepared||player==null||verseStarts.length==0)return;
        for(int i=0;i<verseNumbers.length;i++)if(verseNumbers[i]==pendingVerse){seekingVerse=true;seekTargetMs=verseStarts[i];player.seekTo(seekTargetMs,MediaPlayer.SEEK_CLOSEST);return;}
        pendingVerse=0;
    }
    boolean key(KeyEvent event){
        int code=event.getKeyCode();if(code==KeyEvent.KEYCODE_BACK||code==KeyEvent.KEYCODE_ESCAPE){if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())back();return true;}
        if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;lastBackAt=0;
        if(code==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||code==KeyEvent.KEYCODE_MEDIA_PLAY||code==KeyEvent.KEYCODE_MEDIA_PAUSE){if(event.getRepeatCount()==0)togglePlayback();return true;}
        if(code==KeyEvent.KEYCODE_MEDIA_STOP){savePlayback();releasePlayer();return true;}
        if(code==KeyEvent.KEYCODE_MEDIA_NEXT||code==KeyEvent.KEYCODE_MEDIA_PREVIOUS){seekVerse(code==KeyEvent.KEYCODE_MEDIA_NEXT?1:-1);return true;}
        boolean horizontal=code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT;
        int delta=NativeTvPolicy.horizontalStep(rtl(),code==KeyEvent.KEYCODE_DPAD_LEFT);
        if(horizontal&&page.equals("memory")){if(event.getRepeatCount()==0)turnOrMemory(delta);return true;}
        if(horizontal&&page.equals("weather")&&weatherPage!=null&&weatherPage.hasFocus())return weatherPage.move(delta);
        if(horizontal&&page.equals("reading")&&(root.findFocus()==bodyScroll||(sideLeft!=null&&sideLeft.hasFocus())||(sideRight!=null&&sideRight.hasFocus()))){turn(delta);return true;}
        return false;
    }
    private boolean updateBusy(){return updatePhase.equals("checking")||updatePhase.equals("downloading")||updatePhase.equals("verifying")||updatePhase.equals("installing");}
    private void refreshUpdateBadge(){
        if(updateBadge==null)return;
        boolean available=runtime.optBoolean("updateAvailable")&&UpdatePolicy.shouldShowNotice(runtime.optLong("installedVersion"),runtime.optLong("updateVersion"));
        String label="يتوفر تحديث جديد";if(updateBusy())label=updatePhase.equals("downloading")?"تنزيل "+(updateProgress>=0?updateProgress+"%":"…"):updatePhase.equals("checking")?"جارٍ البحث…":updatePhase.equals("installing")?"التثبيت…":"جارٍ التحقق…";
        updateBadge.state(available,updateBusy(),label,resumed);
    }
    void updateState(String json){try{JSONObject state=new JSONObject(json);updatePhase=state.optString("phase","idle");updateProgress=state.optInt("progress",-1);refreshUpdateBadge();if(updatePhase.equals("error"))message("تعذّر التحديث",state.optString("message"));else if(updatePhase.equals("current"))message("تحديث التطبيق","أنت مثبت أحدث نسخة متاحة من ذِكرى.");}catch(Exception ignored){}}
    void runtimeState(String json){try{runtime=new JSONObject(json);}catch(Exception ignored){}clock.setText(format("HH:mm",AdhanScheduler.now(owner)));refreshDashboard();refreshUpdateBadge();}
    void adhan(boolean active){if(active&&player!=null&&prepared){try{player.pause();paused=true;}catch(Exception ignored){}}if(active)notice("الأذان · اضغط أي زر للإيقاف");}
    void resume(){resumed=true;owner.checkNativeUpdate();refreshUpdateBadge();sky.setIntensity(number("tv_sky",65));main.removeCallbacks(tick);main.post(tick);if(System.currentTimeMillis()-weatherFetched>900000)loadWeather(true);if(prepared){main.removeCallbacks(audioTick);main.post(audioTick);}}
    void pause(){resumed=false;if(updateBadge!=null)updateBadge.stopPulse();stopPreview(false);savePlayback();main.removeCallbacks(tick);if(player!=null&&prepared){try{player.pause();paused=true;}catch(Exception ignored){}}}
    void close(){closed=true;pause();if(choicesDialog!=null)choicesDialog.dismiss();requestId++;io.shutdownNow();main.removeCallbacksAndMessages(null);releasePlayer();if(migration!=null){migration.destroy();migration=null;}if(gps!=null)try{owner.getSystemService(LocationManager.class).removeUpdates(gps);}catch(Exception ignored){}motion.close();}
    private void resetMenu(){choose("إدارة البيانات",new String[]{"مسح سجل القراءة","مسح سجل الاستماع","مسح الموقع والمواقيت","إعادة الضبط الكامل"},i->prompt("تأكيد المسح","حذف البيانات المحددة؟",null,new String[]{"حذف","رجوع"},answer->{if(answer!=0)return;if(i==0){data.edit().remove("tv_read_page").remove("zikra_mushaf_page_v1").apply();}if(i==1){savePlayback();releasePlayer();data.edit().remove("tv_last_url").remove("tv_last_name").remove("tv_resume_ms").remove("quran_bookmark").apply();}if(i==2||i==3){AdhanScheduler.resetLocalData(owner,i==3);data.edit().remove("zikra_city").remove("zikra_lat").remove("zikra_lon").remove("tv_weather").apply();weather=null;}if(i==3){releasePlayer();android.webkit.WebStorage.getInstance().deleteOrigin("https://appassets.androidplatform.net");owner.getPreferences(0).edit().remove("legacyStorage").apply();data.edit().clear().putBoolean("web_migrated",true).apply();owner.getSharedPreferences("zikra_runtime",0).edit().clear().apply();}page="home";history.clear();render(true);}));}
    /** One-time import reads only old same-origin storage. It never loads the old UI or network. */
    private void migrateOnce(){if(data.getBoolean("web_migrated",false))return;
        migration=new WebView(owner);migration.getSettings().setJavaScriptEnabled(true);migration.getSettings().setDomStorageEnabled(true);
        migration.setWebViewClient(new WebViewClient(){
            public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream("<!doctype html><meta charset=utf-8><body></body>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
            public void onPageFinished(WebView view,String url){if(closed||migration!=view)return;view.evaluateJavascript("(function(){var out={};for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);out[k]=localStorage.getItem(k)}return JSON.stringify(out)})()",encoded->{if(closed||migration!=view)return;try{Object value=new JSONTokener(encoded).nextValue();if(!(value instanceof String))throw new Exception();JSONObject saved=new JSONObject((String)value);SharedPreferences.Editor edit=data.edit();java.util.Iterator<String> keys=saved.keys();while(keys.hasNext()){String key=keys.next();if(!data.contains(key)&&saved.opt(key) instanceof String)edit.putString(key,saved.getString(key));}edit.putBoolean("web_migrated",true).commit();page="home";render(false);loadContent();loadReaders();}catch(Exception ignored){notice("تعذّر استيراد بعض الإعدادات القديمة. بقيت النسخة المحفوظة دون حذف.");}finally{view.destroy();migration=null;}});}
        });migration.loadUrl("https://appassets.androidplatform.net/assets/native-migration.html");
    }
}
