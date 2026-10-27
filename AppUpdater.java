package com.zikra.tv;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-initiated signed APK update, with the operating system's installer consent. */
public final class AppUpdater {
    public static final int PERMISSION_REQUEST = 4103, INSTALL_REQUEST = 4104;
    public static final String PREFERENCES = "zikra_updates";
    private final WeakReference<MainActivity> activity;
    private final Context context;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile String state = "{\"phase\":\"idle\",\"message\":\"البحث عن أحدث نسخة على ذِكْرَىٰ\"}";
    private volatile boolean closed;
    private boolean resumed,awaitingInstaller,leftForInstaller;
    private volatile String deferredInstall;

    public AppUpdater(MainActivity owner) { activity = new WeakReference<>(owner); context = owner.getApplicationContext(); }
    public String getState() { return state; }
    public void cleanupInstalledDownloads() { worker.execute(() -> { try { File file = apkFile(context); if (file.isFile() && validate(file) <= version(installedInfo())) { file.delete(); new File(file.getParentFile(), "latest.part").delete(); } } catch (Exception ignored) {} }); }
    private SharedPreferences preferences() { return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE); }
    public static File apkFile(Context context) { return new File(context.getFilesDir(), "updates/latest.apk"); }

    private void report(String phase, String message, int progress) {
        JSONObject value = new JSONObject();
        try { value.put("phase", phase).put("message", message).put("progress", progress); } catch (Exception ignored) {}
        state = value.toString();
        MainActivity owner = activity.get();
        if (owner != null && !closed) owner.runOnUiThread(() -> owner.deliverUpdateState(state));
    }

    public void start() {
        if (closed || !busy.compareAndSet(false, true)) return;
        report("checking", "جارٍ البحث عن أحدث إصدار…", -1);
        worker.execute(() -> {
            try { checkAndDownload(); }
            catch (Exception error) { if (!closed) report("error", friendly(error), -1); }
            finally { busy.set(false); MainActivity owner = activity.get(); if (owner != null && !closed) owner.runOnUiThread(this::continueDeferredInstall); }
        });
    }

    private void checkAndDownload() throws Exception {
        JSONObject release = json("https://api.github.com/repos/" + UpdatePolicy.REPOSITORY + "/releases/latest?t=" + System.currentTimeMillis());
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) throw new Exception("لم يُنشر إصدار مستقر بعد.");
        JSONArray assets = release.optJSONArray("assets");
        JSONObject apk = null, manifestAsset = null;
        if (assets != null) for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i); String name = asset.optString("name");
            if (name.equals("update.json")) manifestAsset = asset;
            if (name.equals("Zikratv.apk")) apk = asset;
        }
        if (apk == null) throw new Exception("الإصدار المنشور لا يحتوي ملف التثبيت Zikratv.apk.");
        String apkUrl = apk.optString("browser_download_url");
        if (!UpdatePolicy.isReleaseAsset(apkUrl)) throw new Exception("رابط التحديث لا يعود إلى مشروع ذِكْرَىٰ.");
        long publishedSize = apk.optLong("size", 0);
        if (publishedSize <= 0 || publishedSize > UpdatePolicy.MAX_APK_BYTES) throw new Exception("حجم ملف التحديث غير صالح.");
        long installed = version(installedInfo());
        String expectedDigest = apk.optString("digest", "").replaceFirst("^sha256:", "");
        long expectedVersion = 0;
        if (manifestAsset != null) {
            String manifestUrl = manifestAsset.optString("browser_download_url");
            if (!UpdatePolicy.isReleaseAsset(manifestUrl)) throw new Exception("بيانات الإصدار غير صالحة.");
            JSONObject manifest = json(manifestUrl + (manifestUrl.contains("?") ? "&" : "?") + "check=" + System.currentTimeMillis());
            if (!context.getPackageName().equals(manifest.optString("packageName")) || !"Zikratv.apk".equals(manifest.optString("apkName"))) throw new Exception("بيانات الإصدار ليست لتطبيق ذِكْرَىٰ.");
            expectedVersion = manifest.optLong("versionCode", 0);
            String manifestDigest = manifest.optString("sha256");
            if (expectedVersion <= 0 || !manifestDigest.matches("(?i)[0-9a-f]{64}")) throw new Exception("بيانات التحديث غير مكتملة. حاول بعد قليل.");
            if (!expectedDigest.isEmpty() && !UpdatePolicy.digestMatches(manifestDigest, expectedDigest)) throw new Exception("جارٍ نشر إصدار جديد. حاول التحديث بعد قليل.");
            expectedDigest = manifestDigest;
            if (expectedVersion <= installed) { report("current", "أنت تستخدم أحدث نسخة متاحة · بناء " + installed, 100); return; }
        }
        File directory = new File(context.getFilesDir(), "updates");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new Exception("تعذّر تجهيز مكان تنزيل التحديث.");
        File target = apkFile(context), part = new File(directory, "latest.part");
        boolean cached = target.isFile() && !expectedDigest.isEmpty() && UpdatePolicy.digestMatches(expectedDigest, digest(target));
        if (!cached) {
            if (directory.getUsableSpace() < publishedSize * 2 + 5 * 1024 * 1024) throw new Exception("المساحة المتاحة لا تكفي للتحديث.");
            download(apkUrl + (apkUrl.contains("?") ? "&" : "?") + "build=" + expectedVersion, part, publishedSize);
        }
        File candidate = cached ? target : part;
        report("verifying", "جارٍ التحقق من النسخة وتوقيعها…", 100);
        try {
            if (!expectedDigest.isEmpty() && !UpdatePolicy.digestMatches(expectedDigest, digest(candidate))) throw new Exception("ملف التحديث غير مكتمل. حاول التنزيل مجددًا.");
            long candidateVersion = validate(candidate);
            if (expectedVersion > 0 && candidateVersion != expectedVersion) throw new Exception("ملف التحديث لا يطابق الإصدار المنشور. حاول بعد قليل.");
            if (candidateVersion <= installed) { report("current", "أنت تستخدم أحدث نسخة متاحة · بناء " + installed, 100); return; }
            if (!cached) {
                if (target.exists() && !target.delete()) throw new Exception("تعذّر استبدال ملف التحديث السابق.");
                if (!part.renameTo(target)) throw new Exception("تعذّر حفظ ملف التحديث.");
            }
            final String candidateDigest = digest(target);
            deferredInstall = candidateDigest;
        } finally { if (part.exists()) part.delete(); }
    }

    private void download(String address, File file, long expectedSize) throws Exception {
        HttpURLConnection connection = open(address);
        try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(file)) {
            byte[] buffer = new byte[32768]; long total = 0, last = 0; int count;
            report("downloading", "جارٍ تنزيل النسخة الجديدة…", 0);
            while ((count = input.read(buffer)) != -1) {
                if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("تم إيقاف التنزيل.");
                total += count;
                if (total > UpdatePolicy.MAX_APK_BYTES || total > expectedSize) throw new Exception("حجم الملف لا يطابق النسخة المنشورة.");
                output.write(buffer, 0, count);
                long now = System.currentTimeMillis();
                if (now - last >= 250) { int percent = (int)(total * 100 / expectedSize); report("downloading", "جارٍ تنزيل النسخة الجديدة… " + percent + "٪", percent); last = now; }
            }
            output.getFD().sync();
            if (total != expectedSize) throw new Exception("انقطع التنزيل قبل اكتماله. حاول مجددًا.");
        } catch (Exception error) { file.delete(); throw error; }
        finally { connection.disconnect(); }
    }

    private void prepareInstall(String verifiedDigest) {
        if (closed) return;
        if (!resumed) { deferredInstall = verifiedDigest; report("ready", "اكتمل التنزيل. ارجع إلى ذِكْرَىٰ لمتابعة التثبيت.", 100); return; }
        preferences().edit().putString("candidateDigest", verifiedDigest).apply();
        MainActivity owner = activity.get(); if (owner == null) return;
        if (Build.VERSION.SDK_INT >= 26 && !context.getPackageManager().canRequestPackageInstalls()) {
            report("permission", "اسمح لذِكْرَىٰ بتثبيت التحديث، ثم ارجع لإكماله.", 100);
            preferences().edit().putBoolean("permissionPending", true).apply();
            try { owner.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.getPackageName())), PERMISSION_REQUEST); }
            catch (Exception error) { preferences().edit().putBoolean("permissionPending", false).apply(); report("error", "هذا الجهاز لا يتيح فتح إذن تثبيت التحديث. راجع إعدادات التطبيقات.", -1); }
            return;
        }
        install(verifiedDigest);
    }

    private void install(String expectedDigest) {
        if (!busy.compareAndSet(false, true)) { deferredInstall = expectedDigest; return; }
        report("verifying", "جارٍ تجهيز التثبيت…", 100);
        worker.execute(() -> {
            try {
                File file = apkFile(context);
                if (!UpdatePolicy.digestMatches(expectedDigest, digest(file))) throw new Exception("ملف التحديث تغيّر. أعد التنزيل.");
                long targetVersion = validate(file);
                if (targetVersion <= version(installedInfo())) { report("current", "أنت تستخدم أحدث نسخة متاحة.", 100); return; }
                MainActivity owner = activity.get();
                if (owner != null && !closed) owner.runOnUiThread(() -> {
                    if (!resumed) { deferredInstall = expectedDigest; report("ready", "ارجع إلى ذِكْرَىٰ لمتابعة التثبيت.", 100); return; }
                    Uri uri = UpdateFileProvider.uri(context);
                    Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT, false);
                    intent.setClipData(ClipData.newRawUri("Zikra update", uri));
                    preferences().edit().putLong("restartVersion", targetVersion).putLong("restartDeadline", System.currentTimeMillis() + 30 * 60 * 1000).apply();
                    report("installing", "وافق على التثبيت، ثم اضغط «فتح» لتشغيل النسخة الجديدة.", 100);
                    try { awaitingInstaller=true;leftForInstaller=false;owner.startActivity(intent); }
                    catch (Exception error) { clearRestart(); report("error", "لم يتم العثور على مثبّت تطبيقات متاح على هذا الجهاز.", -1); }
                });
            } catch (Exception error) { report("error", friendly(error), -1); }
            finally { busy.set(false); }
        });
    }

    public void onResume() {
        resumed = true;
        if(awaitingInstaller&&leftForInstaller){awaitingInstaller=false;leftForInstaller=false;clearRestart();report("idle","يمكنك البحث عن التحديث مجددًا.",-1);}
        if (preferences().getBoolean("permissionPending", false)) {
            preferences().edit().putBoolean("permissionPending", false).apply();
            if (Build.VERSION.SDK_INT < 26 || context.getPackageManager().canRequestPackageInstalls()) deferredInstall = preferences().getString("candidateDigest", "");
            else report("error", "لم يُمنح إذن التثبيت. اضغط تحديث للمحاولة مجددًا.", -1);
        }
        continueDeferredInstall();
    }
    private void continueDeferredInstall() {
        if (closed || deferredInstall == null || busy.get()) return;
        if (!resumed) { report("ready", "اكتمل التنزيل. ارجع إلى ذِكْرَىٰ لمتابعة التثبيت.", 100); return; }
        String value = deferredInstall; deferredInstall = null; prepareInstall(value);
    }
    public void onPause() { resumed = false;if(awaitingInstaller)leftForInstaller=true; }
    public void onInstallResult(int result) {
        clearRestart();
        report(result == Activity.RESULT_OK ? "installed" : "cancelled", result == Activity.RESULT_OK ? "اكتمل تحديث ذِكْرَىٰ." : "لم يكتمل التثبيت. يمكنك المحاولة مجددًا.", -1);
    }
    private void clearRestart() { preferences().edit().remove("restartVersion").remove("restartDeadline").apply(); }
    public void close() { closed = true; worker.shutdownNow(); }
    private String friendly(Exception error) {
        String message = error.getMessage();
        return message != null && message.matches("(?s).*[\\u0600-\\u06ff].*") ? message : "تعذّر التحديث. تحقق من الإنترنت وحاول مجددًا.";
    }

    private PackageInfo installedInfo() throws Exception { return context.getPackageManager().getPackageInfo(context.getPackageName(), signatureFlags()); }
    private int signatureFlags() { return Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES; }
    private static long version(PackageInfo info) { return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode; }
    private long validate(File file) throws Exception {
        PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), signatureFlags()), installed = installedInfo();
        if (archive == null || !context.getPackageName().equals(archive.packageName)) throw new Exception("الملف ليس تحديثًا صالحًا لتطبيق ذِكْرَىٰ.");
        Set<String> existing=signers(installed),candidate=signers(archive);
        if (!existing.equals(candidate) || candidate.isEmpty()) throw new Exception("توقيع التحديث لا يطابق النسخة المثبتة. المثبت: "+shortSigners(existing)+"، الملف: "+shortSigners(candidate)+". احتفظ بالتطبيق المثبت وأرسل نص الرسالة لمراجعة مفتاح التوقيع.");
        return version(archive);
    }
    private static String shortSigners(Set<String> values){if(values.isEmpty())return "غير متاح";StringBuilder out=new StringBuilder();for(String value:values){if(out.length()>0)out.append(", ");out.append(value.substring(0,Math.min(12,value.length())));}return out.toString();}
    private Set<String> signers(PackageInfo info) throws Exception {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28 ? (info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners()) : info.signatures;
        Set<String> result = new HashSet<>();
        if (signatures != null) for (Signature signature : signatures) result.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
        return result;
    }
    private static String digest(File file) throws Exception { MessageDigest hash = MessageDigest.getInstance("SHA-256");try (InputStream input = new FileInputStream(file)) {byte[] buffer = new byte[32768];int count;while ((count = input.read(buffer)) != -1) hash.update(buffer, 0, count);}return hex(hash.digest()); }
    private static String hex(byte[] bytes) { StringBuilder result = new StringBuilder();for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));return result.toString(); }
    private JSONObject json(String address) throws Exception {
        HttpURLConnection connection = open(address);
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {byte[] buffer = new byte[8192];int count;while ((count = input.read(buffer)) != -1) {if (output.size() + count > 1024 * 1024) throw new Exception("بيانات الإصدار أكبر من المتوقع.");output.write(buffer, 0, count);}return new JSONObject(output.toString("UTF-8"));}
        finally { connection.disconnect(); }
    }
    private HttpURLConnection open(String address) throws Exception {
        for (int redirects = 0; redirects < 6; redirects++) {
            if (!UpdatePolicy.isDownloadUrl(address)) throw new Exception("تم رفض رابط تحديث غير موثوق.");
            HttpURLConnection connection = (HttpURLConnection)new URL(address).openConnection();
            connection.setUseCaches(false); connection.setConnectTimeout(15000); connection.setReadTimeout(20000); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Zikra-Android-Updater");connection.setRequestProperty("Accept", new URL(address).getHost().equals("api.github.com") ? "application/vnd.github+json" : "application/octet-stream");connection.setRequestProperty("Cache-Control", "no-cache");
            int status = connection.getResponseCode();
            if (status >= 300 && status <= 399) {String next = connection.getHeaderField("Location");connection.disconnect();if (next == null) break;address = new URL(new URL(address), next).toString();continue;}
            if (status != 200) {connection.disconnect();throw new Exception(status == 403 || status == 429 ? "وصل البحث إلى حد ذِكْرَىٰ المؤقت. حاول لاحقًا." : "تعذّر الوصول إلى الإصدار المنشور. تحقق من الإنترنت.");}
            return connection;
        }
        throw new Exception("تعذّر الوصول إلى ملف التحديث.");
    }
}
