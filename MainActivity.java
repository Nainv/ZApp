
package com.zikra.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.Gravity;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private WebView webView;
    private FrameLayout root;
    private TextView errorView;

    private boolean pageReady = false;
    private boolean updateCheckStarted = false;

    private static final String SITE_URL =
            "https://koukach91-hash.github.io/zikra-android-tv-new/";

    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/koukach91-hash/zikra-android-tv-new/releases/latest";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

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
        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_NEVER_ALLOW
        );

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setWebChromeClient(new WebChromeClient());

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidTV");

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                pageReady = true;
                view.requestFocus();

                if (!updateCheckStarted) {
                    updateCheckStarted = true;
                    checkForUpdates();
                }
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    android.webkit.WebResourceError error
            ) {
                super.onReceivedError(view, request, error);

                if (request.isForMainFrame()) {
                    showError();
                }
            }
        });

        root.addView(
                webView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        setContentView(root);

        if (savedInstanceState == null) {
            webView.loadUrl(SITE_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    /*
     * إرسال أوامر الوسائط إلى JavaScript داخل الموقع.
     * يجب أن يعرّف index.html الدالة
     * window.handleAndroidMediaKey(command).
     */
    private void sendMediaCommand(String command) {
        if (webView == null || !pageReady) {
            return;
        }

        String js =
                "if (typeof window.handleAndroidMediaKey === 'function') {" +
                "window.handleAndroidMediaKey('" + command + "');" +
                "}";

        webView.evaluateJavascript(js, null);
    }

    private void sendBackToWeb() {
        if (webView == null || !pageReady) {
            return;
        }

        webView.evaluateJavascript(
                "(function() {" +
                "if (typeof window.handleAndroidBack === 'function') {" +
                "window.handleAndroidBack();" +
                "}" +
                "})();",
                null
        );
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();

        if (event.getAction() == KeyEvent.ACTION_DOWN) {

            if (errorView != null) {
                if (code == KeyEvent.KEYCODE_BACK
                        || code == KeyEvent.KEYCODE_ESCAPE) {
                    retryLoading();
                    return true;
                }

                return super.dispatchKeyEvent(event);
            }

            if (code == KeyEvent.KEYCODE_BACK
                    || code == KeyEvent.KEYCODE_ESCAPE) {
                sendBackToWeb();
                return true;
            }

            switch (code) {

                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    sendMediaCommand("play");
                    return true;

                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    sendMediaCommand("pause");
                    return true;

                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    sendMediaCommand("toggle");
                    return true;

                case KeyEvent.KEYCODE_MEDIA_STOP:
                    sendMediaCommand("stop");
                    return true;

                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    sendMediaCommand("next");
                    return true;

                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    sendMediaCommand("previous");
                    return true;
            }
        }

        /*
         * الأسهم وOK تُترك إلى WebView،
         * حتى تستطيع صفحة HTML معالجة التنقل والتركيز.
         */
        return super.dispatchKeyEvent(event);
    }

    private void checkForUpdates() {
        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                connection = (HttpURLConnection)
                        new URL(LATEST_RELEASE_API).openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty(
                        "Accept",
                        "application/vnd.github+json"
                );
                connection.setRequestProperty(
                        "User-Agent",
                        "Zikra-Android-TV"
                );

                if (connection.getResponseCode()
                        != HttpURLConnection.HTTP_OK) {
                    return;
                }

                StringBuilder body = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                connection.getInputStream(),
                                "UTF-8"
                        )
                )) {
                    String line;

                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                    }
                }

                JSONObject release =
                        new JSONObject(body.toString());

                String tag = release.optString("tag_name", "");
                String latestVersion =
                        tag.startsWith("v") ? tag.substring(1) : tag;

                String downloadUrl = "";

                JSONArray assets = release.optJSONArray("assets");

                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.optJSONObject(i);

                        if (asset != null
                                && "app-release.apk".equals(
                                asset.optString("name")
                        )) {
                            downloadUrl = asset.optString(
                                    "browser_download_url",
                                    ""
                            );
                            break;
                        }
                    }
                }

                if (isNewerVersion(
                        latestVersion,
                        BuildConfig.VERSION_NAME
                ) && !downloadUrl.isEmpty()) {

                    final String finalUrl = downloadUrl;

                    runOnUiThread(() ->
                            showUpdateDialog(latestVersion, finalUrl)
                    );
                }

            } catch (Exception ignored) {
                // التحديث اختياري؛ يبقى التطبيق يعمل عند فشل الاتصال.
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private boolean isNewerVersion(
            String latest,
            String current
    ) {
        try {
            String[] a = latest.split("\\.");
            String[] b = current.split("\\.");

            for (int i = 0; i < Math.max(a.length, b.length); i++) {
                int av = i < a.length
                        ? Integer.parseInt(
                                a[i].replaceAll("[^0-9].*$", "")
                        )
                        : 0;

                int bv = i < b.length
                        ? Integer.parseInt(
                                b[i].replaceAll("[^0-9].*$", "")
                        )
                        : 0;

                if (av != bv) {
                    return av > bv;
                }
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private void showUpdateDialog(
            String version,
            String downloadUrl
    ) {
        if (isFinishing()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("تحديث جديد لذِكرى")
                .setMessage(
                        "يتوفر إصدار جديد " + version +
                        " من التطبيق. هل تريد تنزيله الآن؟"
                )
                .setPositiveButton(
                        "تنزيل التحديث",
                        (dialog, which) -> {
                            try {
                                Intent intent = new Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(downloadUrl)
                                );
                                startActivity(intent);
                            } catch (Exception ignored) {
                            }
                        }
                )
                .setNegativeButton("لاحقاً", null)
                .show();
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private class AndroidBridge {

        @JavascriptInterface
        public void exitApp() {
            runOnUiThread(() -> finish());
        }
    }

    private void showError() {
        if (errorView != null) {
            return;
        }

        errorView = new TextView(this);
        errorView.setText("يرجى الانتظار والمحاولة من جديد");
        errorView.setTextColor(Color.WHITE);
        errorView.setTextSize(22);
        errorView.setGravity(Gravity.CENTER);
        errorView.setPadding(32, 32, 32, 32);
        errorView.setBackgroundColor(Color.rgb(48, 75, 58));
        errorView.setFocusable(true);
        errorView.setFocusableInTouchMode(true);
        errorView.setOnClickListener(v -> retryLoading());

        root.addView(
                errorView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        errorView.requestFocus();
    }

    private void retryLoading() {
        if (errorView != null) {
            root.removeView(errorView);
            errorView = null;
        }

        pageReady = false;

        if (webView != null) {
            webView.loadUrl(SITE_URL);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();

        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }

        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) {
            webView.saveState(outState);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
