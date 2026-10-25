/* One calendar-day model shared by home and standalone prayer views. */
(() => {
  'use strict';
  const keys=['Fajr','Dhuhr','Asr','Maghrib','Isha'],formatters=new Map();
  const now=()=>window.ZikraClock?.now()??Date.now();
  function parts(value,zone){if(!formatters.has(zone))formatters.set(zone,new Intl.DateTimeFormat('en-GB',{timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'}));return Object.fromEntries(formatters.get(zone).formatToParts(value).filter(p=>p.type!=='literal').map(p=>[p.type,Number(p.value)]))}
  const date=(value,zone)=>{const p=parts(value,zone);return [p.year,String(p.month).padStart(2,'0'),String(p.day).padStart(2,'0')].join('-')};
  function epoch(day,time,zone){const [y,m,d]=day.split('-').map(Number),[h,min]=time.split(' ')[0].split(':').map(Number),wanted=Date.UTC(y,m-1,d,h,min);let result=wanted;for(let i=0;i<4;i++){const p=parts(new Date(result),zone);result+=wanted-Date.UTC(p.year,p.month-1,p.day,p.hour,p.minute,p.second)}return result}
  const tomorrow=day=>{const [y,m,d]=day.split('-').map(Number);return new Date(Date.UTC(y,m-1,d+1)).toISOString().slice(0,10)};
  const clockTime=value=>value?.match(/T(\d{2}:\d{2})/)?.[1]||value?.split(' ')[0];
  const schedule=(day,timings,zone)=>keys.filter(key=>/^([01]?\d|2[0-3]):[0-5]\d$/.test(clockTime(timings[key])||'')).map(key=>({key,date:day,time:clockTime(timings[key]),epoch:timings[key].includes('T')?Date.parse(timings[key]):epoch(day,timings[key],zone)}));
  const plain=timings=>Object.fromEntries(Object.entries(timings).map(([key,value])=>[key,clockTime(value)]));
  async function json(url){const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),12000);try{const response=await fetch(url,{signal:controller.signal});if(!response.ok)throw Error('تعذّر الاتصال بمصدر المواقيت.');return await response.json()}finally{clearTimeout(timer)}}
  async function load(p){
    let lat=p.lat,lon=p.lon;if(Number.isFinite(p.mosque?.lat)&&Number.isFinite(p.mosque?.lon)){lat=p.mosque.lat;lon=p.mosque.lon}
    if(!Number.isFinite(lat)||Math.abs(lat)>90||!Number.isFinite(lon)||Math.abs(lon)>180)throw Error('حدد موقعك أو اختر المدينة من الإعدادات لإظهار المواقيت.');
    p={...p,lat,lon};
    const official=p.mosque?.id==='erfurt_erfurter-moschee'&&locationIsNative();
    const cacheKey=['iso3',lat.toFixed(4),lon.toFixed(4),p.method||3,official?'erfurt':'calculated'].join('|');
    let cached=null;try{const value=JSON.parse(localStorage.getItem('zikra_prayer_cache_v2')||'null');if(value?.key===cacheKey&&value.snapshot?.date===date(new Date(now()),value.snapshot.zone))cached=value.snapshot}catch(_){}
    if(cached&&now()-cached.fetchedAt>=0&&now()-cached.fetchedAt<30*60*1000)return {...cached,selectedMosque:p.mosque?.title||''};
    try{
    if(official)try{const calendar=await json('/assets/_api/erfurt-timetable');if(calendar.error)throw Error();if(calendar.serverEpoch)window.ZikraClock?.sync(calendar.serverEpoch);const today=date(new Date(now()),calendar.zone),nextDate=tomorrow(today),read=day=>{const [year,month,number]=day.split('-').map(Number);if(year!==Number(today.slice(0,4)))return {};const values=calendar.calendar[month-1]?.[String(number)];return values?Object.fromEntries(['Fajr','Sunrise','Dhuhr','Asr','Maghrib','Isha'].map((k,i)=>[k,values[i]])):{}};const timings=read(today);if(!timings.Fajr)throw Error();const next=read(nextDate);const snapshot={zone:calendar.zone,date:today,timings,schedule:[...schedule(today,timings,calendar.zone),...schedule(nextDate,next,calendar.zone)],source:'مواقيت المسجد · مواقيت',sourceUrl:'https://mawaqit.net/en/erfurter-moschee-erfurt-99085-germany',kind:'mosque',selectedMosque:p.mosque.title,nextDayAvailable:!!next.Fajr,fetchedAt:now()};try{localStorage.setItem('zikra_prayer_cache_v2',JSON.stringify({key:cacheKey,snapshot}))}catch(_){}return snapshot}catch(_){}
    const url=day=>'https://api.aladhan.com/v1/timings/'+day.split('-').reverse().join('-')+'?latitude='+p.lat+'&longitude='+p.lon+'&method='+Number(p.method||3)+'&iso8601=true';
    let today=date(new Date(now()),Intl.DateTimeFormat().resolvedOptions().timeZone),first=(await json(url(today))).data,zone=first.meta.timezone;
    const actual=date(new Date(now()),zone);if(actual!==today){today=actual;first=(await json(url(today))).data}
    const nextDate=tomorrow(today);let next=null;try{next=(await json(url(nextDate))).data}catch(_){}
    const snapshot={zone,date:today,timings:plain(first.timings),schedule:[...schedule(today,first.timings,zone),...schedule(nextDate,next?.timings||{},zone)].sort((a,b)=>a.epoch-b.epoch),hijri:first.date.hijri,source:'مواقيت الموقع · حساب فلكي',kind:'location',selectedMosque:p.mosque?.title||'',nextDayAvailable:!!next,fetchedAt:now()};
    try{localStorage.setItem('zikra_prayer_cache_v2',JSON.stringify({key:cacheKey,snapshot}))}catch(_){}return snapshot;
    }catch(error){if(cached)return {...cached,offline:true,source:'مواقيت الموقع المحفوظة لليوم',selectedMosque:p.mosque?.title||''};throw Error('تعذّر جلب المواقيت. تحقق من الإنترنت؛ إعدادات موقعك محفوظة.')}
  }
  function locationIsNative(){return typeof location!=='undefined'&&location.origin==='https://appassets.androidplatform.net'}
  function display(snapshot,now=window.ZikraClock?.now()??Date.now()){
    const next=snapshot?.schedule?.find(item=>item.epoch>now)||null,timings={...snapshot?.timings};
    if(next)timings[next.key]=next.time;
    return {next,timings,nextDay:next&&next.date!==snapshot.date};
  }
  const remaining=milliseconds=>{const seconds=Math.max(0,Math.ceil(milliseconds/1000));return [Math.floor(seconds/3600),Math.floor(seconds/60)%60,seconds%60].map(v=>String(v).padStart(2,'0')).join(':')};
  window.ZikraPrayer={load,display,remaining,date,epoch,schedule,tomorrow};
})();
