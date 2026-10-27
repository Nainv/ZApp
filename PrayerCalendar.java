package com.zikra.tv;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.Locale;
import org.json.JSONObject;
import org.json.JSONArray;

/** Fixed provider endpoints; calendars do not supply or change the app clock. */
public final class PrayerCalendar {
    private static JSONObject erfurtCache;
    private static long erfurtFetched=-1,erfurtAttempt=-30000;
    private static boolean erfurtFailed;
    private static String request(String address) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setUseCaches(false);c.setConnectTimeout(10000);c.setReadTimeout(10000);c.setRequestProperty("User-Agent","Zikra-TV");
        try{if(c.getResponseCode()!=200)throw new java.io.IOException();try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int count;while((count=in.read(bytes))!=-1){out.write(bytes,0,count);if(out.size()>4194304)throw new java.io.IOException();}return out.toString("UTF-8");}}finally{c.disconnect();}
    }
    public static synchronized JSONObject erfurt() throws Exception {
        long elapsed=android.os.SystemClock.elapsedRealtime();
        if(erfurtCache!=null&&elapsed-erfurtFetched<1800000L)return erfurtCache;
        if(erfurtFailed&&elapsed-erfurtAttempt<30000L)throw new java.io.IOException();
        erfurtAttempt=elapsed;erfurtFailed=true;
        String text=request("https://mawaqit.net/en/erfurter-moschee-erfurt-99085-germany");java.util.regex.Matcher m=java.util.regex.Pattern.compile("let confData = (\\{[^\\r\\n]+\\});").matcher(text);if(!m.find())throw new java.io.IOException();JSONObject data=new JSONObject(m.group(1));erfurtCache=new JSONObject().put("name",data.getString("name")).put("zone",data.getString("timezone")).put("calendar",data.getJSONArray("calendar"));erfurtFetched=android.os.SystemClock.elapsedRealtime();erfurtFailed=false;return erfurtCache;
    }
    private static String day(Calendar c){return String.format(Locale.US,"%04d-%02d-%02d",c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH));}
    private static void append(JSONArray result,String day,JSONObject times,String zone,long now) throws Exception {
        for(String key:AdhanScheduler.KEYS){String time=times.optString(key,"");if(time.isEmpty())continue;try{long epoch=PrayerAlarmPolicy.epoch(day,time,zone);result.put(new JSONObject().put("key",key).put("epoch",epoch).put("date",day));}catch(IllegalArgumentException ignored){}}
    }
    public static JSONArray load(JSONObject config,long now) throws Exception {
        String zone=config.optString("zone","UTC");Calendar current=Calendar.getInstance(TimeZone.getTimeZone(zone));current.setTimeInMillis(now);JSONArray result=new JSONArray();
        if("erfurt_erfurter-moschee".equals(config.optString("mosque"))) {
            try{JSONObject source=erfurt();zone=source.getString("zone");current=Calendar.getInstance(TimeZone.getTimeZone(zone));current.setTimeInMillis(now);int year=current.get(Calendar.YEAR);JSONArray months=source.getJSONArray("calendar");for(int n=0;n<40&&current.get(Calendar.YEAR)==year;n++,current.add(Calendar.DATE,1)){JSONArray values=months.getJSONObject(current.get(Calendar.MONTH)).optJSONArray(String.valueOf(current.get(Calendar.DAY_OF_MONTH)));if(values==null)continue;JSONObject times=new JSONObject();String[] keys={"Fajr","Sunrise","Dhuhr","Asr","Maghrib","Isha"};for(int i=0;i<keys.length;i++)times.put(keys[i],values.optString(i,""));append(result,day(current),times,zone,now);}if(result.length()>0)return result;throw new java.io.IOException();}catch(Exception unavailable){if("mosque".equals(config.optString("kind")))throw unavailable;}
        }
        double lat=config.getDouble("lat"),lon=config.getDouble("lon");if(Double.isNaN(lat)||Double.isInfinite(lat)||Double.isNaN(lon)||Double.isInfinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IllegalArgumentException();
        int method=config.optInt("method",3);if(method<1||method>23)method=3;current.set(Calendar.DAY_OF_MONTH,1);
        for(int month=0;month<2;month++,current.add(Calendar.MONTH,1)){
            String url="https://api.aladhan.com/v1/calendar/"+current.get(Calendar.YEAR)+"/"+(current.get(Calendar.MONTH)+1)+"?latitude="+lat+"&longitude="+lon+"&method="+method+"&iso8601=true";
            JSONArray days=new JSONObject(request(url)).getJSONArray("data");for(int i=0;i<days.length();i++){JSONObject data=days.getJSONObject(i),greg=data.getJSONObject("date").getJSONObject("gregorian");String[] parts=greg.getString("date").split("-");append(result,parts[2]+"-"+parts[1]+"-"+parts[0],data.getJSONObject("timings"),data.getJSONObject("meta").getString("timezone"),now);}
        }
        return result;
    }
}
