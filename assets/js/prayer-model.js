/* One calendar-day model shared by home and standalone prayer views. */
(() => {
  'use strict';
  const keys=['Fajr','Dhuhr','Asr','Maghrib','Isha'],formatters=new Map();
  function parts(value,zone){if(!formatters.has(zone))formatters.set(zone,new Intl.DateTimeFormat('en-GB',{timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'}));return Object.fromEntries(formatters.get(zone).formatToParts(value).filter(p=>p.type!=='literal').map(p=>[p.type,Number(p.value)]))}
  const date=(value,zone)=>{const p=parts(value,zone);return [p.year,String(p.month).padStart(2,'0'),String(p.day).padStart(2,'0')].join('-')};
  function epoch(day,time,zone){const [y,m,d]=day.split('-').map(Number),[h,min]=time.split(' ')[0].split(':').map(Number),wanted=Date.UTC(y,m-1,d,h,min);let result=wanted;for(let i=0;i<4;i++){const p=parts(new Date(result),zone);result+=wanted-Date.UTC(p.year,p.month-1,p.day,p.hour,p.minute,p.second)}return result}
  const tomorrow=day=>{const [y,m,d]=day.split('-').map(Number);return new Date(Date.UTC(y,m-1,d+1)).toISOString().slice(0,10)};
  const schedule=(day,timings,zone)=>keys.filter(key=>/^([01]?\d|2[0-3]):[0-5]\d(?: |$)/.test(timings[key]||'')).map(key=>({key,date:day,time:timings[key].split(' ')[0],epoch:epoch(day,timings[key],zone)}));
  async function json(url){const response=await fetch(url);if(!response.ok)throw Error('تعذّر الاتصال بمصدر المواقيت.');return response.json()}
  async function load(p){
    if(p.mosque){
      const calendar=p.mosque.calendar,zone=p.mosque.zone;
      if(!calendar||!zone)throw Error('لم يُربط جدول المسجد الرسمي بعد. اختر ملف مواقيته من الإعدادات.');
      const today=date(new Date(),zone),timings=calendar[today];if(!timings)throw Error('جدول المسجد لا يتضمن مواقيت اليوم.');
      return {zone,date:today,timings,schedule:[...schedule(today,timings,zone),...schedule(tomorrow(today),calendar[tomorrow(today)]||{},zone)],source:p.mosque.title+' · جدول مستورد',kind:'mosque',nextDayAvailable:!!calendar[tomorrow(today)]};
    }
    const url=day=>'https://api.aladhan.com/v1/timings/'+day.split('-').reverse().join('-')+'?latitude='+p.lat+'&longitude='+p.lon+'&method='+Number(p.method||3);
    let today=date(new Date(),Intl.DateTimeFormat().resolvedOptions().timeZone),first=(await json(url(today))).data,zone=first.meta.timezone;
    const actual=date(new Date(),zone);if(actual!==today){today=actual;first=(await json(url(today))).data}
    const nextDate=tomorrow(today);let next=null;try{next=(await json(url(nextDate))).data}catch(_){}
    return {zone,date:today,timings:first.timings,schedule:[...schedule(today,first.timings,zone),...schedule(nextDate,next?.timings||{},zone)].sort((a,b)=>a.epoch-b.epoch),hijri:first.date.hijri,source:'حساب فلكي · AlAdhan',kind:'calculation',nextDayAvailable:!!next};
  }
  function display(snapshot,now=Date.now()){
    const next=snapshot?.schedule?.find(item=>item.epoch>now)||null,timings={...snapshot?.timings};
    if(next)timings[next.key]=next.time;
    return {next,timings,nextDay:next&&next.date!==snapshot.date};
  }
  const remaining=milliseconds=>{const seconds=Math.max(0,Math.ceil(milliseconds/1000));return [Math.floor(seconds/3600),Math.floor(seconds/60)%60,seconds%60].map(v=>String(v).padStart(2,'0')).join(':')};
  window.ZikraPrayer={load,display,remaining,date,epoch,schedule,tomorrow};
})();
