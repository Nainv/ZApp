package com.zikra.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;

/** Opt-in only. Android/OEM background-launch restrictions are respected. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent event) {
        if(!Intent.ACTION_BOOT_COMPLETED.equals(event.getAction()) || !context.getSharedPreferences("zikra_runtime",0).getBoolean("autostart",false)) return;
        Intent launch=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try { context.startActivity(launch); } catch(Exception ignored) {}
        try {
            NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            String lang=context.getSharedPreferences("zikra_runtime",0).getString("language","ar");
            String title=lang.equals("de")?"Zikra öffnen":lang.equals("en")?"Open Zikra":"افتح ذِكرى";
            if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel("zikra_start",title,NotificationManager.IMPORTANCE_DEFAULT));
            PendingIntent intent=PendingIntent.getActivity(context,0,launch,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,"zikra_start"):new Notification.Builder(context);
            manager.notify(217,b.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText(title).setContentIntent(intent).setAutoCancel(true).build());
        }catch(Exception ignored){}
    }
}
