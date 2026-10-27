package com.zikra.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;

/** Offers Open after an update explicitly requested from Zikra; installation still uses Android consent. */
public final class UpdateRestartReceiver extends BroadcastReceiver {
    public static final int OPEN_NOTIFICATION=2201;
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        AdhanScheduler.restore(context,false);
        SharedPreferences preferences = context.getSharedPreferences(AppUpdater.PREFERENCES, Context.MODE_PRIVATE);
        long target = preferences.getLong("restartVersion", 0), deadline = preferences.getLong("restartDeadline", 0);
        preferences.edit().remove("restartVersion").remove("restartDeadline").apply();
        if (target <= 0 || System.currentTimeMillis() > deadline) return;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            long version = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            if (version < target) return;
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (launch == null) return;
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
            android.app.NotificationManager manager=context.getSystemService(android.app.NotificationManager.class);if(manager==null)return;
            android.app.Notification.Builder notice;
            if(Build.VERSION.SDK_INT>=26){android.app.NotificationChannel channel=new android.app.NotificationChannel("zikra_updates","تحديثات ذِكرى",android.app.NotificationManager.IMPORTANCE_HIGH);channel.setSound(null,null);channel.enableVibration(false);manager.createNotificationChannel(channel);notice=new android.app.Notification.Builder(context,"zikra_updates");}else notice=new android.app.Notification.Builder(context);
            android.app.PendingIntent open=android.app.PendingIntent.getActivity(context,2201,launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP),android.app.PendingIntent.FLAG_UPDATE_CURRENT|android.app.PendingIntent.FLAG_IMMUTABLE);
            notice.setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("تم تحديث ذِكرى").setContentText("فتح التطبيق بالنسخة الجديدة").setContentIntent(open).setAutoCancel(true).addAction(android.R.drawable.ic_media_play,"فتح التطبيق",open);
            manager.notify(OPEN_NOTIFICATION,notice.build());
        } catch (Exception ignored) { /* The system installer still provides Open when notifications are unavailable. */ }
    }
}
