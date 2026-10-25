package com.zikra.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONObject;

public final class AdhanReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String action=intent.getAction();if(AdhanService.STOP.equals(action)){AdhanService.stop(context);return;}if(!"com.zikra.tv.PRAYER".equals(action)){AdhanScheduler.restore(context,Intent.ACTION_BOOT_COMPLETED.equals(action));return;}
        String key=intent.getStringExtra("key");long epoch=intent.getLongExtra("epoch",0);JSONObject setting=AdhanScheduler.settings(context,key);
        long played=AdhanScheduler.prefs(context).getLong("played_"+key,0);
        if(AdhanScheduler.allowed(context)&&PrayerAlarmPolicy.prayer(key)&&setting!=null&&setting.optBoolean("enabled",true)&&AdhanScheduler.expected(context,key,epoch)&&PrayerAlarmPolicy.deliver(epoch,AdhanScheduler.now(context),played)){
            AdhanScheduler.prefs(context).edit().putLong("played_"+key,epoch).apply();Intent service=new Intent(context,AdhanService.class).putExtra("key",key).putExtra("epoch",epoch);
            try{if(Build.VERSION.SDK_INT>=26)context.startForegroundService(service);else context.startService(service);}catch(Exception denied){AdhanService.notifyFallback(context,key);}
        }
        AdhanScheduler.reschedule(context);
    }
}
