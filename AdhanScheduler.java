package com.zikra.tv;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Persisted native alarms survive a closed WebView and ordinary app dismissal. */
public final class AdhanScheduler {
    public static final String[] KEYS={"Fajr","Dhuhr","Asr","Maghrib","Isha"};
    private static final int JOB=3181;private static final ExecutorService worker=Executors.newSingleThreadExecutor();private static boolean refreshing,refreshQueued;
    public static SharedPreferences prefs(Context c){return c.getSharedPreferences("zikra_adhan",0);}
    public static long now(Context c){SharedPreferences p=prefs(c);long anchor=p.getLong("clockElapsed",-1),elapsed=SystemClock.elapsedRealtime();return anchor>=0&&elapsed>=anchor?p.getLong("clockUtc",0)+elapsed-anchor:System.currentTimeMillis();}
    public static void clock(Context c,long epoch){SharedPreferences p=prefs(c);if(p.getLong("clockElapsed",-1)>=0&&SystemClock.elapsedRealtime()>=p.getLong("clockElapsed",-1)&&Math.abs(now(c)-epoch)<2000)return;p.edit().putLong("clockElapsed",SystemClock.elapsedRealtime()).putLong("clockUtc",epoch).apply();reschedule(c);}
    public static JSONObject configuration(Context c){try{return new JSONObject(prefs(c).getString("config","{}"));}catch(Exception ignored){return new JSONObject();}}
    public static boolean exact(Context c){return Build.VERSION.SDK_INT<31||((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();}
    private static String identity(JSONObject p){return p.optString("lat")+"|"+p.optString("lon")+"|"+p.optInt("method",3)+"|"+p.optString("mosque")+"|"+p.optString("zone")+"|"+p.optString("kind");}
    public static synchronized void update(Context c,String payload){
        try{JSONObject data=new JSONObject(payload),config=data.getJSONObject("config"),settings=data.getJSONObject("settings");JSONArray incoming=data.optJSONArray("schedule");if(payload.length()>131072)throw new IllegalArgumentException();
            SharedPreferences p=prefs(c);boolean changed=!identity(config).equals(identity(configuration(c)));
            p.edit().putString("config",config.toString()).putString("settings",settings.toString()).putString("schedule",changed?(incoming==null?"[]":incoming.toString()):merge(p.getString("schedule","[]"),incoming)).apply();
            reschedule(c);scheduleJob(c);refreshAsync(c);AdhanService.stopIfDisabled(c);
        }catch(Exception ignored){}
    }
    private static String merge(String saved,JSONArray fresh)throws Exception{JSONArray result=new JSONArray(),old=new JSONArray(saved);java.util.HashSet<String> keys=new java.util.HashSet<>();if(fresh!=null)for(int i=0;i<fresh.length();i++){JSONObject e=fresh.getJSONObject(i);keys.add(e.optString("date")+"|"+e.optString("key"));result.put(e);}for(int i=0;i<old.length();i++){JSONObject e=old.getJSONObject(i);if(!keys.contains(e.optString("date")+"|"+e.optString("key")))result.put(e);}return result.toString();}
    public static JSONObject settings(Context c,String key){try{return new JSONObject(prefs(c).getString("settings","{}")).optJSONObject(key);}catch(Exception ignored){return null;}}
    public static int pendingCount(Context c){int count=0;long now=now(c);try{JSONArray entries=new JSONArray(prefs(c).getString("schedule","[]"));for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i),s=settings(c,e.optString("key"));if(e.optLong("epoch")>now&&s!=null&&s.optBoolean("enabled",true))count++;}}catch(Exception ignored){}return count;}
    public static synchronized void reschedule(Context c){
        AlarmManager manager=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);long now=now(c);
        try{JSONArray entries=new JSONArray(prefs(c).getString("schedule","[]"));for(int i=0;i<KEYS.length;i++){String key=KEYS[i];JSONObject setting=settings(c,key);long[] options=new long[entries.length()];long played=prefs(c).getLong("played_"+key,0);for(int j=0;j<entries.length();j++){JSONObject e=entries.getJSONObject(j);long epoch=e.optLong("epoch");if(key.equals(e.optString("key"))&&epoch!=played)options[j]=epoch;}long next=PrayerAlarmPolicy.next(options,now);
                Intent intent=new Intent(c,AdhanReceiver.class).setAction("com.zikra.tv.PRAYER").putExtra("key",key).putExtra("epoch",next);PendingIntent pending=PendingIntent.getBroadcast(c,3100+i,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);manager.cancel(pending);
                if(setting==null||!setting.optBoolean("enabled",true)||next==0)continue;long trigger=SystemClock.elapsedRealtime()+Math.max(1,next-now);
                try{if(exact(c))manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,trigger,pending);else manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,trigger,pending);}catch(SecurityException ignored){manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,trigger,pending);}
            }}catch(Exception ignored){}
    }
    public static boolean expected(Context c,String key,long epoch){try{JSONArray entries=new JSONArray(prefs(c).getString("schedule","[]"));for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);if(key.equals(e.optString("key"))&&epoch==e.optLong("epoch"))return true;}}catch(Exception ignored){}return false;}
    public static void refreshAsync(Context context){Context c=context.getApplicationContext();synchronized(AdhanScheduler.class){if(refreshing){refreshQueued=true;return;}refreshing=true;}worker.execute(()->{try{refresh(c);}finally{boolean queued;synchronized(AdhanScheduler.class){refreshing=false;queued=refreshQueued;refreshQueued=false;}if(queued)refreshAsync(c);}});}
    public static void refresh(Context c){
        try{SharedPreferences p=prefs(c);JSONObject config=configuration(c);if(!config.has("lat")||config.isNull("lat"))return;String id=identity(config);long now=now(c);JSONArray existing=new JSONArray(p.getString("schedule","[]"));long latest=0;for(int i=0;i<existing.length();i++)latest=Math.max(latest,existing.getJSONObject(i).optLong("epoch"));
            if(now-p.getLong("fetched",0)>=43200000L||latest<now+7*86400000L){JSONArray entries=PrayerCalendar.load(config,now);synchronized(AdhanScheduler.class){if(!id.equals(identity(configuration(c))))return;if(entries.length()>0)p.edit().putString("schedule",entries.toString()).putLong("fetched",now).apply();}}
        }catch(Exception ignored){}finally{JSONObject config=configuration(c);if(config.has("lat")&&!config.isNull("lat"))AdhanService.cacheVoices(c);reschedule(c);}
    }
    public static void scheduleJob(Context c){if(!configuration(c).has("lat")||configuration(c).isNull("lat"))return;try{JobScheduler jobs=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);for(JobInfo job:jobs.getAllPendingJobs())if(job.getId()==JOB)return;jobs.schedule(new JobInfo.Builder(JOB,new ComponentName(c,AdhanRefreshJob.class)).setPersisted(true).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(12*60*60*1000L).build());}catch(Exception ignored){}}
    public static void restore(Context c,boolean reboot){if(reboot)prefs(c).edit().remove("clockElapsed").remove("clockUtc").apply();reschedule(c);scheduleJob(c);refreshAsync(c);}
}
