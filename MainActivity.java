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
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                pageReady = false;
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageReady = true;
                view.requestFocus();
            }
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
    private void sendDpadToWeb(String direction) {
        if (webView == null || !pageReady) return;
        webView.evaluateJavascript(
            "if (typeof window.handleAndroidDpad === 'function') { window.handleAndroidDpad('" + direction + "'); }",
            null
        );
    }

    private void sendMediaCommand(String command) {
        if (webView == null || !pageReady) return;
        webView.evaluateJavascript(
            "if (typeof window.handleAndroidMediaKey === 'function') { window.handleAndroidMediaKey('" + command + "'); }",
            null
        );
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            // Consume matching key-up events for keys handled below so WebView does not
            // receive a second, inconsistent navigation event.
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_STOP:
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                case KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD:
                case KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD:
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

        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                sendDpadToWeb("left"); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                sendDpadToWeb("right"); return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                sendDpadToWeb("up"); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                sendDpadToWeb("down"); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (webView != null && pageReady) {
                    webView.evaluateJavascript(
                        "(function(){var e=document.activeElement;if(e){if(e.tagName==='SELECT'){e.click();}else{e.click();}}else{var b=document.querySelector('#quranBtn');if(b)b.click();}})();",
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
            case KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                sendMediaCommand("next"); return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                sendMediaCommand("previous"); return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    @Override protected void onResume() { super.onResume(); hideSystemBars(); if (webView != null) webView.onResume(); }
    @Override protected void onPause() { if (webView != null) webView.onPause(); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle outState) { if (webView != null) webView.saveState(outState); super.onSaveInstanceState(outState); }
    @Override protected void onDestroy() { if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; } super.onDestroy(); }
}
