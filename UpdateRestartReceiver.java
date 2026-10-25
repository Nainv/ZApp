package com.zikra.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;

/** Reopens only an update explicitly requested from Zikra, when Android allows it. */
public final class UpdateRestartReceiver extends BroadcastReceiver {
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
            if (launch != null) context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (Exception ignored) { /* System installer/launcher still provides Open on restricted devices. */ }
    }
}
