package com.zikra.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.Gravity;

public class MainActivity extends Activity {
    private WebView webView;
    private FrameLayout root;
    private TextView errorView;
    private static final String SITE_URL = "https://koukach91-hash.github.io/zikra-android-tv-new/";
    private boolean pageReady = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemBars();
        root = new FrameLayout(this);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidTV");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return false; }
            @Override public void onPageFinished(WebView view, String url) { super.onPageFinished(view, url); pageReady = true; view.requestFocus(); }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error); if (request.isForMainFrame()) showError();
            }
        });
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        if (savedInstanceState == null) webView.loadUrl(SITE_URL); else webView.restoreState(savedInstanceState);
    }
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    private class AndroidBridge {
        @JavascriptInterface public void exitApp() { runOnUiThread(() -> finish()); }
    }
    private void sendBackToWeb() {
        if (webView == null || !pageReady) return;
        webView.evaluateJavascript("(function(){if(typeof window.handleAndroidBack==='function'){window.handleAndroidBack();}})();", null);
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
        pageReady = false; if (webView != null) webView.loadUrl(SITE_URL);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                if (errorView != null) { retryLoading(); return true; }
                sendBackToWeb(); return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onResume() { super.onResume(); hideSystemBars(); if (webView != null) webView.onResume(); }
    @Override protected void onPause() { if (webView != null) webView.onPause(); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle outState) { if (webView != null) webView.saveState(outState); super.onSaveInstanceState(outState); }
    @Override protected void onDestroy() { if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; } super.onDestroy(); }
}
