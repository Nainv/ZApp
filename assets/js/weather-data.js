(() => {
  'use strict';let memory=null,inflight=null;
  function today(zone){return new Intl.DateTimeFormat('sv-SE',{timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date())}
  function valid(value,key){return value?.key===key&&value.data?.daily?.time?.[0]===today(value.data.timezone)}
  function normalize(data){const format=new Intl.DateTimeFormat('en-GB',{timeZone:data.timezone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'});const iso=seconds=>{const p=Object.fromEntries(format.formatToParts(new Date(seconds*1000)).filter(v=>v.type!=='literal').map(v=>[v.type,v.value]));return p.year+'-'+p.month+'-'+p.day+'T'+p.hour+':'+p.minute};data.hourly.epochs=data.hourly.time.slice();data.hourly.time=data.hourly.time.map(iso);data.daily.time=data.daily.time.map(t=>new Date((t+data.utc_offset_seconds)*1000).toISOString().slice(0,10));data.daily.sunrise=data.daily.sunrise.map(iso);data.daily.sunset=data.daily.sunset.map(iso);data.current.time=iso(data.current.time);return data}
  async function load(p){
    if(!ZikraLocation.valid(p.lat,p.lon))throw Error('اختر الموقع من الإعدادات لإظهار الطقس.');
    const key=p.lat.toFixed(4)+','+p.lon.toFixed(4);let cached=memory;
    if(!valid(cached,key))try{cached=JSON.parse(localStorage.getItem('zikra_weather_cache_v3')||'null')}catch(_){}
    if(valid(cached,key)&&Date.now()-cached.savedAt<5*60*1000){memory=cached;return cached.data}
    if(inflight?.key===key)return inflight.promise;if(inflight)inflight.controller.abort();
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),15000);
    const promise=(async()=>{try{
      const url='https://api.open-meteo.com/v1/forecast?latitude='+p.lat+'&longitude='+p.lon+'&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,is_day&hourly=temperature_2m,relative_humidity_2m,weather_code,precipitation_probability,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max,sunrise,sunset&timezone=auto&forecast_days=10&timeformat=unixtime';
      const response=await fetch(url,{signal:controller.signal});if(!response.ok)throw Error('weather');const data=normalize(await response.json());memory={key,data,savedAt:Date.now()};try{localStorage.setItem('zikra_weather_cache_v3',JSON.stringify(memory))}catch(_){}return data;
    }catch(error){if(valid(cached,key))return {...cached.data,offline:true};throw Error('تعذّر جلب الطقس. تحقق من الإنترنت ثم أعد المحاولة.')}finally{clearTimeout(timer);if(inflight?.controller===controller)inflight=null}})();
    inflight={key,controller,promise};return promise;
  }
  window.ZikraWeatherData={load,normalize};
})();
