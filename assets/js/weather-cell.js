(() => {
  'use strict';
  const $=id=>document.getElementById(id),strip=$('weatherDays'),hours=$('weatherHours');
  let data=null,selected=0,hourIndex=0,locationKey='',dayButtons=[],hourButtons=[],version='',active=true;
  const dayFormat=new Intl.DateTimeFormat('ar',{weekday:'long'}),dateFormat=new Intl.DateTimeFormat('ar',{day:'numeric',month:'short'});
  const degrees=value=>Number.isFinite(value)?Math.round(value)+'°':'—',time=value=>value?.slice(11,16)||'—';
  function metric(label){const node=document.createElement('div');node.className='day-metric';const name=document.createElement('small'),value=document.createElement('strong');name.textContent=label;node.append(name,value);$('dayMetrics').append(node);return value}
  const metrics=['الشروق','الغروب','احتمال المطر','الرياح','الرطوبة','الحالة'].map(metric);
  function focus(node){node?.focus({preventScroll:true});node?.scrollIntoView({block:'nearest',inline:'nearest',behavior:'instant'})}
  function selectHour(index,move=false){if(!hourButtons.length)return;hourIndex=(index+hourButtons.length)%hourButtons.length;const i=hourButtons[hourIndex].dataset.index,h=data.hourly;$('selectedHourInfo').textContent=time(h.time[i])+' · '+ZikraWeatherScene.describe(h.weather_code[i]).label+' · المطر '+(h.precipitation_probability[i]||0)+'٪';if(move)focus(hourButtons[hourIndex])}
  function selectDay(index,move=false){
    if(!data)return;const old=selected;selected=(index+dayButtons.length)%dayButtons.length;
    dayButtons[old]?.classList.remove('is-current');dayButtons[selected].classList.add('is-current');
    dayButtons[old]?.setAttribute('aria-pressed','false');dayButtons[selected].setAttribute('aria-pressed','true');
    const d=data.daily,date=d.time[selected],info=ZikraWeatherScene.describe(d.weather_code[selected]);
    $('selectedDayTitle').textContent=dayButtons[selected].querySelector('.day-name').textContent+' · '+dateFormat.format(new Date(date+'T12:00:00'));
    $('selectedCondition').textContent=info.label;$('selectedSymbol').innerHTML=ZikraWeatherScene.icon(d.weather_code[selected]);$('selectedRange').textContent=degrees(d.temperature_2m_max[selected])+' / '+degrees(d.temperature_2m_min[selected]);
    const indexes=[];for(let i=0;i<data.hourly.time.length;i++)if(data.hourly.time[i].startsWith(date))indexes.push(i);
    const humidity=indexes.length?Math.round(indexes.reduce((sum,i)=>sum+data.hourly.relative_humidity_2m[i],0)/indexes.length):null;
    [time(d.sunrise[selected]),time(d.sunset[selected]),(d.precipitation_probability_max[selected]||0)+'٪',Math.round(d.wind_speed_10m_max[selected])+' كم/س',humidity===null?'—':humidity+'٪',info.label].forEach((v,i)=>metrics[i].textContent=v);
    indexes.forEach((i,n)=>{let button=hourButtons[n];if(!button){button=document.createElement('button');button.className='weather-hour';const stamp=document.createElement('span'),icon=document.createElement('span'),temp=document.createElement('strong');stamp.className='hour-time';icon.className='hour-symbol';button.append(stamp,icon,temp);button.addEventListener('focus',()=>selectHour(n));button.addEventListener('click',()=>selectHour(n));hours.append(button);hourButtons[n]=button}button.hidden=false;button.dataset.index=i;button.children[0].textContent=time(data.hourly.time[i]);button.children[1].innerHTML=ZikraWeatherScene.icon(data.hourly.weather_code[i]);button.children[2].textContent=degrees(data.hourly.temperature_2m[i]);button.setAttribute('aria-label',time(data.hourly.time[i])+'، '+degrees(data.hourly.temperature_2m[i]))});
    hourButtons.splice(indexes.length).forEach(node=>node.remove());hourIndex=Math.min(hourIndex,hourButtons.length-1);selectHour(Math.max(0,hourIndex));
    if(move)focus(dayButtons[selected]);
  }
  function apply(result,p,session=false){
    if(!result?.current||!result.daily?.time)return;const nextVersion=p.lat+','+p.lon+'|'+result.current.time+'|'+result.daily.time[0];
    $('place').textContent=p.city;locationKey=p.lat+','+p.lon;
    if(nextVersion===version){if(session)focus(dayButtons[selected]);return}
    version=nextVersion;data=result;strip.replaceChildren();dayButtons=[];
    result.daily.time.slice(0,10).forEach((date,index)=>{const button=document.createElement('button');button.className='forecast-day';const name=document.createElement('span'),stamp=document.createElement('span'),icon=document.createElement('span'),range=document.createElement('span');name.className='day-name';stamp.className='day-date';icon.className='day-symbol';range.className='day-temp';name.textContent=index===0?'اليوم':index===1?'غدًا':dayFormat.format(new Date(date+'T12:00:00'));stamp.textContent=dateFormat.format(new Date(date+'T12:00:00'));icon.innerHTML=ZikraWeatherScene.icon(result.daily.weather_code[index]);range.textContent=degrees(result.daily.temperature_2m_max[index])+' / '+degrees(result.daily.temperature_2m_min[index]);button.append(name,stamp,icon,range);button.addEventListener('focus',()=>{if(selected!==index)selectDay(index)});button.addEventListener('click',()=>selectDay(index));strip.append(button);dayButtons.push(button)});
    $('currentTemp').textContent=degrees(result.current.temperature_2m);$('currentCondition').textContent=ZikraWeatherScene.describe(result.current.weather_code,result.current.is_day).label;$('currentSymbol').innerHTML=ZikraWeatherScene.icon(result.current.weather_code,result.current.is_day);ZikraWeatherScene.mount($('weatherBackdrop'),result.current.weather_code,result.current.is_day);
    $('date').textContent=new Intl.DateTimeFormat('ar',{timeZone:result.timezone,dateStyle:'full'}).format(new Date());$('weatherStatus').textContent=(result.offline?'توقعات محفوظة · ':'')+'الطقس الآن في '+p.city+' · '+result.timezone;selectDay(Math.min(selected,dayButtons.length-1),session||parent===window);
  }
  window.zikraRemoteDelegate={move(direction){
    const current=document.activeElement,di=dayButtons.indexOf(current),hi=hourButtons.indexOf(current);
    if(current===$('cellHomeButton')){if(direction==='down'&&dayButtons.length)focus(dayButtons[selected]);return true}
    if(di>=0){if(direction==='left')selectDay(selected+1,true);else if(direction==='right')selectDay(selected-1,true);else if(direction==='up')focus($('cellHomeButton'));else if(direction==='down')selectHour(hourIndex,true);return true}
    if(hi>=0){if(direction==='left')selectHour(hourIndex+1,true);else if(direction==='right')selectHour(hourIndex-1,true);else if(direction==='up')focus(dayButtons[selected]);return true}
    if(direction==='up')focus($('cellHomeButton'));else if(dayButtons.length)focus(dayButtons[selected]);return true;
  },ok(){return document.activeElement!==$('cellHomeButton')}};
  window.addEventListener('message',event=>{if(event.source!==parent)return;const m=event.data;if(m?.type==='zikra:session-start'||m?.type==='zikra:context'){
    const p=m.preferences;if(!p)return;$('place').textContent=p.city;
    const key=p.lat+','+p.lon;if(key!==locationKey&&!m.weather){data=null;version='';strip.replaceChildren();hours.replaceChildren();dayButtons=[];hourButtons=[];$('weatherStatus').textContent='جارٍ جلب طقس '+p.city+'…'}
    if(m.weather)apply(m.weather,p,m.type==='zikra:session-start');else if(!ZikraLocation.valid(p.lat,p.lon))$('weatherStatus').textContent='اختر الموقع من الإعدادات لإظهار الطقس.';
  }else if(m?.type==='zikra:cell-active'){active=m.active;document.body.classList.toggle('cell-inactive',!active)}});
  if(parent!==window)parent.postMessage({type:'zikra:context-request'},'*');else{const p=ZikraLocation.read();$('place').textContent=p.city;ZikraWeatherData.load(p).then(d=>apply(d,p,true)).catch(error=>$('weatherStatus').textContent=error.message)}
})();
