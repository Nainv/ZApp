package com.zikra.tv;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.HttpURLConnection;
import org.json.JSONObject;

/** Audio is owned here on Android; the WebView only mirrors its state. */
public final class AdhanService extends Service {
    public static final String STATE="com.zikra.tv.ADHAN_STATE";public static final String STOP="com.zikra.tv.ADHAN_STOP";private static final int NOTICE=3190;
    public static volatile String activePrayer="";
    private MediaPlayer player;private AudioManager audio;private AudioFocusRequest focus;private PowerManager.WakeLock wake;private final Handler handler=new Handler(Looper.getMainLooper());
    private final AudioManager.OnAudioFocusChangeListener audioFocus=value->{if(value<0)stopSelf();};
    public static String voiceUrl(String voice){switch(voice){case "madinah":return "https://www.islamcan.com/audio/adhan/azan2.mp3";case "alafasy":return "https://www.islamcan.com/audio/adhan/azan8.mp3";case "syria":return "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Adhan_Syria_(%D8%A3%D8%B0%D8%A7%D9%86_%D8%B3%D9%88%D8%B1%D9%8A%D8%A7).mp3";default:return "https://www.islamcan.com/audio/adhan/azan1.mp3";}}
    private static String safeVoice(String voice){return "madinah".equals(voice)||"alafasy".equals(voice)||"syria".equals(voice)?voice:"makkah";}
    private static File cache(Context c,String voice){return new File(new File(c.getFilesDir(),"adhan"),safeVoice(voice)+".mp3");}
    public static void cacheVoices(Context c){
        java.util.HashSet<String> used=new java.util.HashSet<>();for(String key:AdhanScheduler.KEYS){JSONObject s=AdhanScheduler.settings(c,key);if(s!=null&&s.optBoolean("enabled",true))used.add(safeVoice(s.optString("voice","makkah")));}
        for(String voice:used){File file=cache(c,voice);if(file.length()>1024)continue;file.getParentFile().mkdirs();File temp=new File(file.getPath()+".part");HttpURLConnection connection=null;
            try{connection=(HttpURLConnection)new URL(voiceUrl(voice)).openConnection();connection.setConnectTimeout(10000);connection.setReadTimeout(10000);if(connection.getResponseCode()!=200)continue;String type=connection.getContentType();if(type!=null&&!type.startsWith("audio/")&&!type.contains("octet-stream"))continue;long total=0;try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(temp)){byte[] bytes=new byte[16384];int n;while((n=in.read(bytes))!=-1){total+=n;if(total>12582912)throw new java.io.IOException();out.write(bytes,0,n);}}if(total<=1024||(connection.getContentLength()>=0&&total!=connection.getContentLength())||!temp.renameTo(file))throw new java.io.IOException();}catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();temp.delete();}}
        File dir=new File(c.getFilesDir(),"adhan");File[] files=dir.listFiles();if(files!=null)for(File file:files)if(file.getName().endsWith(".mp3")&&!used.contains(file.getName().replace(".mp3","")))file.delete();
    }
    private static String language(Context c){return AdhanScheduler.configuration(c).optString("language","ar");}
    private static String text(Context c,String ar,String en,String de){String lang=language(c);return "de".equals(lang)?de:"en".equals(lang)?en:ar;}
    private static String prayerName(Context c,String key){switch(key){case "Fajr":return text(c,"الفجر","Fajr","Fadschr");case "Dhuhr":return text(c,"الظهر","Dhuhr","Dhuhr");case "Asr":return text(c,"العصر","Asr","Asr");case "Maghrib":return text(c,"المغرب","Maghrib","Maghrib");case "Isha":return text(c,"العشاء","Isha","Ischa");default:return key;}}
    private static String message(Context c,String key){String city=AdhanScheduler.configuration(c).optString("city","").trim();if(city.isEmpty())city=text(c,"موقعك","your location","deinen Standort");return text(c,"حان الآن موعد أذان ","It is now time for the ","Jetzt ist die Zeit für den ")+prayerName(c,key)+text(c," حسب التوقيت المحلي لمدينة "," adhan, local time in ","-Gebetsruf, Ortszeit in ")+city;}
    private static Notification notification(Context c,String key,boolean fallback){
        NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);String channel=fallback?"zikra_prayer_alert":"zikra_adhan_playback";
        if(Build.VERSION.SDK_INT>=26){NotificationChannel n=new NotificationChannel(channel,text(c,"الأذان","Adhan","Gebetsruf"),NotificationManager.IMPORTANCE_HIGH);if(fallback)n.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());else n.setSound(null,null);manager.createNotificationChannel(n);}
        Intent open=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);PendingIntent content=PendingIntent.getActivity(c,3190,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getBroadcast(c,3191,new Intent(c,AdhanReceiver.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String body=message(c,key);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,channel):new Notification.Builder(c);b.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(text(c,"ذِكْرَىٰ","Zikra","Zikra")).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body)).setContentIntent(content).setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC).setWhen(System.currentTimeMillis()).setAutoCancel(fallback).setOngoing(!fallback);
        if(Build.VERSION.SDK_INT<26){b.setPriority(Notification.PRIORITY_HIGH);if(fallback)b.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),AudioManager.STREAM_ALARM);}
        b.addAction(android.R.drawable.ic_media_pause,text(c,"إيقاف","Stop","Beenden"),stop);b.addAction(android.R.drawable.ic_media_play,text(c,"متابعة","Continue","Weiter"),content);
        return b.build();
    }
    public static void stop(Context c){c.stopService(new Intent(c,AdhanService.class));NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);manager.cancel(NOTICE);manager.cancel(NOTICE+1);}
    public static void notifyFallback(Context c,String key){if(!AdhanScheduler.allowed(c))return;try{((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTICE+1,notification(c,key,true));}catch(Exception ignored){}}
    public static void stopIfDisabled(Context c){if(activePrayer.isEmpty())return;JSONObject s=AdhanScheduler.settings(c,activePrayer);if(!AdhanScheduler.allowed(c)||s==null||!s.optBoolean("enabled",true))c.stopService(new Intent(c,AdhanService.class));}
    private void state(boolean active){sendBroadcast(new Intent(STATE).setPackage(getPackageName()).putExtra("key",activePrayer).putExtra("active",active));}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null||STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        String key=intent.getStringExtra("key");JSONObject settings=AdhanScheduler.settings(this,key);if(!AdhanScheduler.allowed(this)||!PrayerAlarmPolicy.prayer(key)||settings==null||!settings.optBoolean("enabled",true)){stopSelf();return START_NOT_STICKY;}
        activePrayer=key;
        try{startForeground(NOTICE,notification(this,key,false));state(true);wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"zikra:adhan");wake.acquire(330000L);audio=(AudioManager)getSystemService(AUDIO_SERVICE);
            AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();int granted;
            if(Build.VERSION.SDK_INT>=26){focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).setOnAudioFocusChangeListener(audioFocus).build();granted=audio.requestAudioFocus(focus);}else granted=audio.requestAudioFocus(audioFocus,AudioManager.STREAM_ALARM,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            if(granted!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){stopSelf();return START_NOT_STICKY;}
            player=new MediaPlayer();player.setAudioAttributes(attributes);String voice=safeVoice(settings.optString("voice","makkah"));File local=cache(this,voice);player.setDataSource(local.length()>1024?local.getAbsolutePath():voiceUrl(voice));player.setOnPreparedListener(p->p.start());player.setOnCompletionListener(p->stopSelf());player.setOnErrorListener((p,what,extra)->{notifyFallback(this,key);stopSelf();return true;});player.prepareAsync();handler.postDelayed(this::stopSelf,300000);
        }catch(Exception error){notifyFallback(this,key);stopSelf();}
        return START_NOT_STICKY;
    }
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(player!=null){player.release();player=null;}if(audio!=null){if(Build.VERSION.SDK_INT>=26&&focus!=null)audio.abandonAudioFocusRequest(focus);else audio.abandonAudioFocus(audioFocus);}if(wake!=null&&wake.isHeld())wake.release();state(false);activePrayer="";stopForeground(true);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
