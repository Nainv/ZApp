package com.zikra.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.Manifest;
import android.os.Build;
import android.widget.Toast;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.GeolocationPermissions;
import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import org.json.JSONTokener;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.Gravity;
import android.util.Log;

public class MainActivity extends Activity {
    public static volatile boolean appVisible;
    private WebView webView;
    private NativeTvUi nativeTv;
    private FrameLayout root;
    private TextView errorView;
    // Load the bundled interface first so the app can open without internet.
    private static final String ASSET_ORIGIN = "https://appassets.androidplatform.net";
    private static final String LEGACY_URL = "file:///android_asset/index.html";
    private static final String LOCAL_URL = ASSET_ORIGIN + "/assets/index.html";
    private boolean migrating = false;
    private GeolocationPermissions.Callback locationResult;
    private String locationOrigin;
    private static final String TAG = "ZikraRemote";
    private boolean pageReady = false;
    private static final int LOCATION_PERMISSION_REQUEST = 4101;
    private AppUpdater appUpdater;
    private RuntimeSupport runtimeSupport;
    private long keyboardRequest;
    private final java.util.HashSet<Integer> adhanKeys=new java.util.HashSet<>();
    private boolean adhanTouch;
    private boolean stopActiveAdhan(){if(AdhanService.activePrayer.isEmpty())return false;AdhanService.stop(this);deliverAdhanState("",false);return true;}
    private final android.content.BroadcastReceiver adhanEvents=new android.content.BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){deliverAdhanState(i.getStringExtra("key"),i.getBooleanExtra("active",false));}
    };
    private void deliverAdhanState(String key,boolean active){if(nativeTv!=null){nativeTv.adhan(active);return;}if(webView==null||!pageReady)return;try{org.json.JSONObject data=new org.json.JSONObject().put("key",key).put("active",active);webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('zikra:adhan-native',{detail:"+data+"}));",null);}catch(Exception ignored){}}
    private void adhanNotifications(){
        if(Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED){
            if(Build.VERSION.SDK_INT>=24&&!((android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled())openAdhanNotificationSettings();
            return;
        }
        android.content.SharedPreferences p=AdhanScheduler.prefs(this);
        if(p.getBoolean("notificationAsked",false)&&!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)){
            openAdhanNotificationSettings();return;
        }
        p.edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},4104);
    }
    private void openAdhanNotificationSettings(){
        try{if(Build.VERSION.SDK_INT>=26)startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()));else startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception ignored){}
    }
    private String adhanStatus(){org.json.JSONObject s=new org.json.JSONObject();try{s.put("exact",AdhanScheduler.exact(this));s.put("notifications",(Build.VERSION.SDK_INT<24||((android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()));s.put("scheduled",AdhanScheduler.pendingCount(this));s.put("active",AdhanService.activePrayer);}catch(Exception ignored){}return s.toString();}


    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(UpdateRestartReceiver.OPEN_NOTIFICATION);
        appUpdater = new AppUpdater(this);
        AdhanScheduler.restore(this,false);
        android.content.IntentFilter adhanFilter=new android.content.IntentFilter(AdhanService.STATE);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(adhanEvents,adhanFilter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(adhanEvents,adhanFilter);
        runtimeSupport = new RuntimeSupport(this);
        runtimeSupport.setListener(() -> runOnUiThread(this::deliverRuntimeState)); runtimeSupport.check();
        ((android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(217);
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::sendBackToWeb);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemBars();
        // Keep TV landscape; allow phones/tablets to use portrait or rotate naturally.
        boolean isTv = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        setRequestedOrientation(isTv
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        if (isTv && Build.VERSION.SDK_INT >= 25) {
            nativeTv = new NativeTvUi(this);
            nativeTv.show();
            return;
        }
        root = new FrameLayout(this);
        webView = new WebView(this);
        root.setBackgroundColor(Color.rgb(16, 27, 35));
        webView.setBackgroundColor(Color.rgb(16, 27, 35));
        long assetBuild = getBuildCode();
        if (getPreferences(MODE_PRIVATE).getLong("assetBuild", 0) != assetBuild) {
            webView.clearCache(true);
            getPreferences(MODE_PRIVATE).edit().putLong("assetBuild", assetBuild).apply();
            appUpdater.cleanupInstalledDownloads();
        }
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        if (!isTv) {
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setTextZoom(100);
        }
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        // Required for file:///android_asset/index.html. Keep cross-file and
        // universal file access disabled for security.
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                Uri uri = Uri.parse(origin);
                if (!"https".equals(uri.getScheme()) || !"appassets.androidplatform.net".equals(uri.getHost())) { callback.invoke(origin, false, false); return; }
                if (hasLocationPermission()) { callback.invoke(origin, true, false); return; }
                locationResult = callback; locationOrigin = origin;
                requestLocationPermission();
            }
        });
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidTV");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                return !("https".equals(uri.getScheme()) && "appassets.androidplatform.net".equals(uri.getHost()) && uri.getPath() != null && uri.getPath().startsWith("/assets/"));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Keep the native bridge confined to bundled application pages.
                android.net.Uri uri = request.getUrl();
                return !("https".equals(uri.getScheme()) && "appassets.androidplatform.net".equals(uri.getHost()) && uri.getPath().startsWith("/assets/"));
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith(LEGACY_URL + "?storage_migration=")) {
                    return response("text/html", "<!doctype html><meta charset=utf-8><body style='background:#101b23'>");
                }
                android.net.Uri uri = request.getUrl();
                if (!"appassets.androidplatform.net".equals(uri.getHost())) return null;
                String file = uri.getPath();
                if ("https".equals(uri.getScheme()) && "/assets/_api/mosques".equals(file)) return nearbyMosques(uri);
                if ("https".equals(uri.getScheme()) && "/assets/_api/erfurt-timetable".equals(file)) return erfurtTimetable();
                if (!"https".equals(uri.getScheme()) || file == null || !file.startsWith("/assets/") || file.contains("..") || file.indexOf('\\') >= 0) return response("text/plain", "Not found");
                file = file.substring(8);
                try {
                    InputStream stream = getAssets().open(file);
                    String mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(android.webkit.MimeTypeMap.getFileExtensionFromUrl(file));
                    if (file.endsWith(".js")) mime = "application/javascript";
                    else if (file.endsWith(".ttf")) mime = "font/ttf";
                    else if (file.endsWith(".mp3")) mime = "audio/mpeg";
                    if (mime == null) mime = "application/octet-stream";
                    return new WebResourceResponse(mime, "UTF-8", 200, "OK", Collections.singletonMap("Cache-Control", "no-cache"), stream);
                } catch (java.io.IOException error) { return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", Collections.emptyMap(), new ByteArrayInputStream(new byte[0])); }
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                pageReady = false;
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (migrating && url.startsWith(LEGACY_URL)) {
                    view.evaluateJavascript("(function(){try{var data={};for(var i=0;i<localStorage.length;i++){var key=localStorage.key(i);data[key]=localStorage.getItem(key)}return JSON.stringify(data)}catch(e){return '{}'}})()", result -> {
                        try { Object decoded = new JSONTokener(result).nextValue(); if (decoded instanceof String) getPreferences(MODE_PRIVATE).edit().putString("legacyStorage", (String)decoded).apply(); } catch (Exception ignored) {}
                        getPreferences(MODE_PRIVATE).edit().putBoolean("storageCaptured", true).apply();
                        migrating = false;
                        view.loadUrl(bundledUrl());
                    });
                    return;
                }
                pageReady = true;
                deliverRuntimeState();
                view.requestFocus();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error); if (request.isForMainFrame()) showError();
            }
        });
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        // Start from the bundled copy. Background content updates will be added
        // in a separate step, without making every launch depend on GitHub Pages.
        migrating = !getPreferences(MODE_PRIVATE).getBoolean("storageCaptured", false);
        webView.loadUrl(migrating ? LEGACY_URL + "?storage_migration=1" : bundledUrl());
    }
    private long getBuildCode() { try { android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0); return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode; } catch (Exception ignored) { return 0; } }
    private String bundledUrl() {
        try { return LOCAL_URL + "?build=" + getPackageManager().getPackageInfo(getPackageName(), 0).versionCode; }
        catch (Exception ignored) { return LOCAL_URL; }
    }
    private WebResourceResponse response(String mime, String text) {
        return new WebResourceResponse(mime, "UTF-8", new ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private WebResourceResponse erfurtTimetable() {
        try {return response("application/json",PrayerCalendar.erfurt().toString());}catch(Exception ignored){return response("application/json","{\"error\":true}");}
    }
    private WebResourceResponse nearbyMosques(Uri request) {
        HttpURLConnection connection = null;
        try {
            double lat = Double.parseDouble(request.getQueryParameter("lat"));
            double lon = Double.parseDouble(request.getQueryParameter("lon"));
            if (Double.isNaN(lat) || Double.isInfinite(lat) || Math.abs(lat) > 90 || Double.isNaN(lon) || Double.isInfinite(lon) || Math.abs(lon) > 180) throw new IllegalArgumentException();
            // Only this documented, read-only public directory is reachable through the adapter.
            URL endpoint = new URL("https://pray.zone/api/mosques/nearby?latitude=" + lat + "&longitude=" + lon + "&maxDistance=25000");
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setConnectTimeout(10000); connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != 200) throw new java.io.IOException();
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) { if (output.size() + count > 524288) throw new java.io.IOException(); output.write(buffer, 0, count); }
                return new WebResourceResponse("application/json", "UTF-8", 200, "OK", Collections.singletonMap("Cache-Control", "no-store"), new ByteArrayInputStream(output.toByteArray()));
            }
        } catch (Exception error) {
            return new WebResourceResponse("application/json", "UTF-8", 503, "Unavailable", Collections.emptyMap(), new ByteArrayInputStream("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } finally { if (connection != null) connection.disconnect(); }
    }
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    private class AndroidBridge {
        @JavascriptInterface public boolean resetLocalData(String kind) {try{if(!"location".equals(kind)&&!"factory".equals(kind))return false;AdhanScheduler.resetLocalData(MainActivity.this,"factory".equals(kind));if("factory".equals(kind)){getSharedPreferences("zikra_runtime",0).edit().clear().commit();getPreferences(MODE_PRIVATE).edit().remove("legacyStorage").putBoolean("storageCaptured",true).commit();}return true;}catch(Exception error){return false;}}
        @JavascriptInterface public void refreshInterfaceCache() { runOnUiThread(() -> { if(webView != null) webView.clearCache(true); }); }
        @JavascriptInterface public void setAdhanSchedule(String payload) { AdhanScheduler.update(MainActivity.this,payload); }
        @JavascriptInterface public boolean isTelevision() { return getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK); }
        @JavascriptInterface public void stopAdhan() { runOnUiThread(()->{AdhanService.stop(MainActivity.this);deliverAdhanState("",false);}); }
        @JavascriptInterface public String getAdhanStatus() { return adhanStatus(); }
        @JavascriptInterface public void requestAdhanNotifications() { runOnUiThread(MainActivity.this::adhanNotifications); }
        @JavascriptInterface public void requestExactAdhan() { runOnUiThread(()->{if(Build.VERSION.SDK_INT>=31&&!AdhanScheduler.exact(MainActivity.this)){try{startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}catch(Exception ignored){Toast.makeText(MainActivity.this,"اسمح بتنبيهات الأذان من إعدادات النظام",Toast.LENGTH_LONG).show();}}else AdhanScheduler.reschedule(MainActivity.this);}); }
        @JavascriptInterface public String getRuntimeState() { return runtimeSupport.state(); }
        @JavascriptInterface public void checkForUpdate() { runtimeSupport.check(); }
        @JavascriptInterface public void setStartup(boolean enabled, String language) {
            getSharedPreferences("zikra_runtime",0).edit().putBoolean("autostart",enabled).putString("language",language).apply();
            if(enabled && Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)runOnUiThread(()->requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},4103));
        }
        @JavascriptInterface public void showKeyboard() { runOnUiThread(() -> { final long request = ++keyboardRequest; if (webView != null) webView.postDelayed(() -> { if (request == keyboardRequest && webView != null && webView.hasFocus()) ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(webView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT); }, 80); }); }
        @JavascriptInterface public void hideKeyboard() { runOnUiThread(() -> { ++keyboardRequest; if (webView != null) ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(webView.getWindowToken(), 0); }); }
        @JavascriptInterface public void updateApp() { if (appUpdater != null) appUpdater.start(); }
        @JavascriptInterface public String getUpdateState() { return appUpdater == null ? "{}" : appUpdater.getState(); }
        @JavascriptInterface public String getLegacyStorage() { return getPreferences(MODE_PRIVATE).getString("legacyStorage", "{}"); }
        @JavascriptInterface public void exitApp() { runOnUiThread(() -> finish()); }
        // Call this from JavaScript only when the user selects a location-based feature.
        @JavascriptInterface public void requestLocationPermission() {
            runOnUiThread(() -> MainActivity.this.requestLocationPermission());
        }
        @JavascriptInterface public boolean hasLocationPermission() {
            return MainActivity.this.hasLocationPermission();
        }
    }
    private boolean hasLocationPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AppUpdater.INSTALL_REQUEST && appUpdater != null) appUpdater.onInstallResult(resultCode);
    }
    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (webView != null && pageReady) {
                webView.evaluateJavascript("if(window.onAndroidLocationPermissionResult)window.onAndroidLocationPermissionResult(true);", null);
            }
            return;
        }
        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(nativeTv!=null){nativeTv.permissionResult(requestCode,grantResults);return;}
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            boolean granted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (locationResult != null) {
                locationResult.invoke(locationOrigin, granted, false);
                locationResult = null;
            }
            if (webView != null && pageReady) {
                webView.evaluateJavascript("if(window.onAndroidLocationPermissionResult)window.onAndroidLocationPermissionResult(" + granted + ");", null);
            }
            if (!granted) Toast.makeText(this, "لم يتم منح إذن الموقع", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendBackToWeb() {
        if(nativeTv!=null){nativeTv.back();return;}
        if(stopActiveAdhan())return;
        if (webView == null || !pageReady) return;
        webView.evaluateJavascript("(function(){if(typeof window.zikraBackFromRemote==='function'){window.zikraBackFromRemote();return;}if(typeof window.handleAndroidBack==='function'){window.handleAndroidBack();}})();", null);
    }
    private void showError() {
        if (errorView != null) return;
        errorView = new TextView(this); errorView.setText("يرجى الانتظار والمحاولة من جديد");
        errorView.setTextColor(Color.WHITE); errorView.setTextSize(22); errorView.setGravity(Gravity.CENTER);
        errorView.setPadding(32, 32, 32, 32); errorView.setBackgroundColor(Color.rgb(48, 75, 58));
        errorView.setFocusable(true); errorView.setFocusableInTouchMode(true); errorView.setOnClickListener(v -> retryLoading());
        root.addView(errorView, new FrameLayout.LayoutParams(-1, -1)); errorView.requestFocus();
    }
    private void retryLoading() {
        if (errorView != null) { root.removeView(errorView); errorView = null; }
        pageReady = false; if (webView != null) webView.loadUrl(bundledUrl());
    }
    private void sendMediaCommand(String command) {
        if(stopActiveAdhan())return;
        if (webView == null || !pageReady) {
            Log.w(TAG, "Ignoring media command " + command + ": WebView page is not ready");
            return;
        }
        webView.evaluateJavascript(
            "(function(){if(typeof window.handleAndroidMediaKey==='function'){window.handleAndroidMediaKey('" + command + "');return 'handled';}return 'handler-missing';})()",
            value -> { if ("\"handler-missing\"".equals(value)) Log.w(TAG, "HTML media-key handler is missing"); }
        );
    }

    private boolean isMediaCommandKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                return true;
            default:
                return false;
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if(adhanKeys.contains(code)){if(event.getAction()==KeyEvent.ACTION_UP)adhanKeys.remove(code);return true;}
        if(event.getAction()==KeyEvent.ACTION_DOWN&&code!=KeyEvent.KEYCODE_VOLUME_UP&&code!=KeyEvent.KEYCODE_VOLUME_DOWN&&code!=KeyEvent.KEYCODE_VOLUME_MUTE&&stopActiveAdhan()){adhanKeys.add(code);return true;}
        if(nativeTv!=null)return nativeTv.key(event)||super.dispatchKeyEvent(event);
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) return super.dispatchKeyEvent(event);

        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            // Consume matching key-up events for keys handled below so WebView does not
            // receive a second, inconsistent navigation event.
            switch (code) {
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_STOP:
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                case KeyEvent.KEYCODE_HEADSETHOOK:
                    return true;
                default:
                    return super.dispatchKeyEvent(event);
            }
        }

        if (errorView != null && (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE)) {
            retryLoading();
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            sendBackToWeb();
            return true;
        }

        // Physical media buttons often auto-repeat when held. Process each press once.
        if (event.getRepeatCount() > 0 && isMediaCommandKey(code)) return true;

        switch (code) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (webView != null && pageReady) {
                    webView.evaluateJavascript(
                        "(function(){if(typeof window.handleAndroidOk==='function'){window.handleAndroidOk();return;}var e=document.activeElement;if(e){e.click();}else{var b=document.querySelector('#quranBtn');if(b)b.click();}})();",
                        null
                    );
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                sendMediaCommand("play"); return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                sendMediaCommand("pause"); return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                sendMediaCommand("toggle"); return true;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                sendMediaCommand("stop"); return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                sendMediaCommand("next"); return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                sendMediaCommand("previous"); return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    @Override protected void onResume() {
        super.onResume();appVisible=true;
        hideSystemBars();
        if(nativeTv!=null)nativeTv.resume();
        if (webView != null) webView.onResume();
        if (appUpdater != null) appUpdater.onResume();
        AdhanScheduler.reschedule(this);AdhanScheduler.scheduleJob(this);AdhanScheduler.refreshAsync(this);
        if (runtimeSupport != null) { runtimeSupport.check(); deliverRuntimeState(); }
    }
    private void deliverRuntimeState() {
        if(nativeTv!=null&&runtimeSupport!=null){nativeTv.runtimeState(runtimeSupport.state());return;}
        if (webView == null || !pageReady || runtimeSupport == null) return;
        webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('zikra:runtime-state',{detail:JSON.parse(" + org.json.JSONObject.quote(runtimeSupport.state()) + ")}));", null);
    }
    void checkNativeUpdate(){if(runtimeSupport!=null){runtimeSupport.check();deliverRuntimeState();}}
    void startNativeUpdate(){if(appUpdater!=null)appUpdater.start();}
    public void deliverUpdateState(String json) {
        if(nativeTv!=null){nativeTv.updateState(json);return;}
        if (webView == null || !pageReady) return;
        webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('zikra:app-update',{detail:JSON.parse(" + org.json.JSONObject.quote(json) + ")}));", null);
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){
        if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)adhanTouch=stopActiveAdhan();
        if(adhanTouch){if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP||event.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL)adhanTouch=false;return true;}
        return super.dispatchTouchEvent(event);
    }
    @Override public void onBackPressed() { if(!stopActiveAdhan())sendBackToWeb(); }
    @Override protected void onPause() { if(nativeTv!=null)nativeTv.pause();appVisible=false;AdhanScheduler.reschedule(this);AdhanScheduler.scheduleJob(this);AdhanService.stopIfDisabled(this); if (appUpdater != null) appUpdater.onPause(); if (webView != null) webView.onPause(); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle outState) {
        // The bundled page is loaded on creation; don't restore a stale remote page state.
        super.onSaveInstanceState(outState);
    }
    @Override protected void onDestroy() { if(nativeTv!=null)nativeTv.close();try{unregisterReceiver(adhanEvents);}catch(Exception ignored){} if (runtimeSupport != null) runtimeSupport.close(); if (appUpdater != null) appUpdater.close(); if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; } super.onDestroy(); }
}
