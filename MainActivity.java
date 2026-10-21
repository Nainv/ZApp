package com.zikra.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.Gravity;

public class MainActivity extends Activity {
    private WebView webView;
    private static final String SITE_URL = "https://koukach91-hash.github.io/zikra-android-tv-new/";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512 | 4096);
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(false);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                if (host != null && (host.equals("koukach91-hash.github.io") || host.endsWith("github.io"))) return false;
                return false;
            }
            @Override public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) {
                    TextView message = new TextView(MainActivity.this);
                    message.setText("تعذّر فتح ذِكرى. تأكد من اتصال الإنترنت ثم اضغط إعادة المحاولة بالريموت.");
                    message.setTextColor(Color.WHITE); message.setTextSize(24); message.setGravity(Gravity.CENTER); message.setPadding(40,40,40,40);
                    message.setBackgroundColor(Color.rgb(7,28,44));
                    root.removeAllViews(); root.addView(message);
                    message.setFocusable(true); message.requestFocus();
                    message.setOnClickListener(v -> { root.removeAllViews(); root.addView(webView); webView.reload(); });
                }
            }
        });
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        if (savedInstanceState == null) webView.loadUrl(SITE_URL); else webView.restoreState(savedInstanceState);
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) { webView.goBack(); return true; }
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) && webView != null) { webView.requestFocus(); }
        return super.onKeyDown(keyCode, event);
    }
    @Override protected void onSaveInstanceState(Bundle outState) { super.onSaveInstanceState(outState); if (webView != null) webView.saveState(outState); }
    @Override protected void onDestroy() { if (webView != null) webView.destroy(); super.onDestroy(); }
}
