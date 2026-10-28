package com.zikra.tv;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** UTC is synchronized separately from release assets and mosque calendars. */
public final class RuntimeSupport {
    private final Context context;
    private final long installedVersion;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile long utc, anchor, latest;
    private long checked = -1;
    private volatile boolean closed;
    private volatile Runnable listener;
    private volatile boolean checking;
    public RuntimeSupport(Context value) { context = value.getApplicationContext(); installedVersion = installed(); }
    public void setListener(Runnable value) { listener = value; }
    private long installed() { try { return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode; } catch (Exception ignored) { return 0; } }
    private synchronized void sync(long serverTime) { if (serverTime > 1577836800000L) { utc = serverTime; anchor = SystemClock.elapsedRealtime(); AdhanScheduler.clock(context,serverTime); } }
    public synchronized String state() {
        JSONObject result = new JSONObject();
        try { boolean synced = utc > 0;
            result.put("timeEpoch", synced ? utc + SystemClock.elapsedRealtime()-anchor : System.currentTimeMillis());
            result.put("adhanBackground",AdhanScheduler.prefs(context).getBoolean("background",true)); result.put("adhanPrayer",AdhanService.activePrayer); result.put("clockSynced", synced); result.put("installedVersion", installedVersion);
            result.put("updateVersion", latest); result.put("updateAvailable", UpdatePolicy.shouldShowNotice(installedVersion, latest));
            result.put("autostart", context.getSharedPreferences("zikra_runtime", 0).getBoolean("autostart", false));
        } catch (Exception ignored) {} return result.toString();
    }
    private JSONObject request(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(address).openConnection();
        c.setUseCaches(false); c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("User-Agent", "Zikra-TV");c.setRequestProperty("Cache-Control","no-cache");
        try { int status=c.getResponseCode();if(status!=200)throw new java.io.IOException();
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1) {out.write(b,0,n);if(out.size()>1048576)throw new java.io.IOException();}return new JSONObject(out.toString("UTF-8")); }
        } finally { c.disconnect(); }
    }
    private void synchronizeClock() {
        for (String endpoint : new String[]{"https://www.google.com/generate_204", "https://api.github.com/zen"}) {
            HttpURLConnection c = null;
            try {
                c=(HttpURLConnection)new URL(endpoint+"?zikra_clock="+SystemClock.elapsedRealtime()).openConnection();
                c.setRequestMethod("HEAD");c.setUseCaches(false);c.setConnectTimeout(4000);c.setReadTimeout(4000);
                c.setRequestProperty("Cache-Control","no-cache, no-store");c.setRequestProperty("User-Agent","Zikra-TV");
                long started=SystemClock.elapsedRealtime();int status=c.getResponseCode();
                long sample=ClockPolicy.sample(c.getHeaderFieldDate("Date",0),c.getHeaderField("Age"),started,SystemClock.elapsedRealtime());
                if(status>=200&&status<300&&sample>0){sync(sample);Runnable changed=listener;if(!closed&&changed!=null)changed.run();return;}
            } catch(Exception ignored) {} finally {if(c!=null)c.disconnect();}
        }
    }
    public synchronized void check() {
        if(closed || !UpdatePolicy.shouldCheckNotice(SystemClock.elapsedRealtime(), checked, checking))return;
        checking=true;checked=SystemClock.elapsedRealtime();worker.execute(()->{try {
            synchronizeClock();
            JSONObject manifest=request("https://github.com/Nainv/ZApp/releases/download/v1.0.5/update.json?check=" + System.currentTimeMillis());
            if("com.zikra.tv".equals(manifest.optString("packageName")) && "Zikratv.apk".equals(manifest.optString("apkName")) && manifest.optString("sha256").matches("(?i)[0-9a-f]{64}") && manifest.optLong("versionCode",0)>0)latest=manifest.getLong("versionCode");
        }catch(Exception ignored){}finally{checking=false;Runnable changed=listener;if(!closed && changed!=null)changed.run();}});
    }
    public synchronized void close(){closed=true;listener=null;worker.shutdownNow();}
}
