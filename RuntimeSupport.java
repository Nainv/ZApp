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

/** A quiet update check also synchronizes UTC without altering the system clock. */
public final class RuntimeSupport {
    private final Context context;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile long utc, anchor, latest, checked;
    private volatile boolean checking;
    public RuntimeSupport(Context value) { context = value.getApplicationContext(); }
    private long installed() { try { return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode; } catch (Exception ignored) { return 0; } }
    public void sync(long serverTime) { if (serverTime > 1577836800000L) { utc = serverTime; anchor = SystemClock.elapsedRealtime(); } }
    public String state() {
        JSONObject result = new JSONObject();
        try { boolean synced = utc > 0 && SystemClock.elapsedRealtime()-anchor < 86400000L;
            result.put("timeEpoch", synced ? utc + SystemClock.elapsedRealtime()-anchor : System.currentTimeMillis());
            result.put("clockSynced", synced); result.put("installedVersion", installed());
            result.put("updateVersion", latest); result.put("updateAvailable", latest > installed());
            result.put("autostart", context.getSharedPreferences("zikra_runtime", 0).getBoolean("autostart", false));
        } catch (Exception ignored) {} return result.toString();
    }
    private JSONObject request(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("User-Agent", "Zikra-TV");c.setRequestProperty("Cache-Control","no-cache");
        try { int status=c.getResponseCode();long date=c.getHeaderFieldDate("Date",0);try {long age=Long.parseLong(c.getHeaderField("Age"));if(age>0&&age<31536000)date+=age*1000;}catch(Exception ignored){}if(date>0) sync(date);if(status!=200)throw new java.io.IOException();
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1) {out.write(b,0,n);if(out.size()>1048576)throw new java.io.IOException();}return new JSONObject(out.toString("UTF-8")); }
        } finally { c.disconnect(); }
    }
    public synchronized void check() {
        if(checking || checked>0 && SystemClock.elapsedRealtime()-checked<900000L)return;
        checking=true;checked=SystemClock.elapsedRealtime();worker.execute(()->{try {
            JSONObject manifest=request("https://github.com/koukach91-hash/zikra-android-tv-new/releases/latest/download/update.json");
            if("com.zikra.tv".equals(manifest.optString("packageName")) && manifest.optString("sha256").matches("(?i)[0-9a-f]{64}"))latest=manifest.getLong("versionCode");
        }catch(Exception ignored){}finally{checking=false;}});
    }
    public void close(){worker.shutdownNow();}
}
