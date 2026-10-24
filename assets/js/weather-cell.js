(() => {
  'use strict';
  const $=id=>document.getElementById(id),strip=$('weatherDays');let data=null,selected=0;
  const locale=localStorage.getItem('zikra_language')==='ar'?'ar-u-nu-arab':'ar';
  const dayFormat=new Intl.DateTimeFormat(locale,{weekday:'long'}),dateFormat=new Intl.DateTimeFormat(locale,{day:'numeric',month:'short'});
  const degrees=value=>Math.round(value)+'°',clock=value=>value?.slice(11,16)||'—';
  function condition(code){if(code<=1)return['☀','صحو','sun'];if(code<=3)return['☁','غائم','cloud'];if(code<=48)return['☁','ضباب','cloud'];if(code<=67||code>=80&&code<=82)return['☂','أمطار','rain'];if(code<=77||code>=85&&code<=86)return['❄','ثلوج','cloud'];return['ϟ','عواصف','rain']}
  function metric(label,value){const item=document.createElement('span');item.className='day-metric';const l=document.createElement('small'),v=document.createElement('strong');l.textContent=label;v.textContent=value;item.append(l,v);return item}
  function select(index,focus){if(!data)return;selected=(index+data.daily.time.length)%data.daily.time.length;const days=[...strip.children];days.forEach((card,i)=>{card.classList.toggle('is-current',i===selected);card.setAttribute('aria-pressed',String(i===selected))});document.body.dataset.scene=condition(data.daily.weather_code[selected])[2];if(focus)days[selected].focus({preventScroll:true});days[selected].scrollIntoView({block:'nearest',inline:'center',behavior:'instant'})}
  function build(){
    strip.replaceChildren();const daily=data.daily,hourly=data.hourly;
    daily.time.forEach((date,index)=>{
      const button=document.createElement('button');button.type='button';button.className='forecast-day';button.dataset.day=String(index);button.dir='rtl';
      const name=document.createElement('span');name.className='day-name';name.textContent=index===0?'اليوم':index===1?'غدًا':dayFormat.format(new Date(date+'T12:00:00'));
      const stamp=document.createElement('span');stamp.className='day-date';stamp.textContent=dateFormat.format(new Date(date+'T12:00:00'));
      const info=condition(daily.weather_code[index]),icon=document.createElement('span');icon.className='day-icon';icon.textContent=info[0];icon.setAttribute('aria-hidden','true');
      const desc=document.createElement('span');desc.className='day-condition';desc.textContent=info[1];
      const range=document.createElement('span');range.className='day-range';range.innerHTML='<span class="low"></span><span></span>';range.children[0].textContent=degrees(daily.temperature_2m_min[index]);range.children[1].textContent=degrees(daily.temperature_2m_max[index]);
      const detail=document.createElement('span');detail.className='day-detail';const stats=document.createElement('span');stats.className='day-metrics';
      const indexes=hourly.time.map((t,i)=>t.startsWith(date)?i:-1).filter(i=>i>=0),humidity=Math.round(indexes.reduce((sum,i)=>sum+hourly.relative_humidity_2m[i],0)/(indexes.length||1));
      stats.append(metric('الشروق',clock(daily.sunrise[index])),metric('الغروب',clock(daily.sunset[index])),metric('المطر',(daily.precipitation_probability_max[index]||0)+'٪'),metric('الرياح',Math.round(daily.wind_speed_10m_max[index])+' كم/س'),metric('الرطوبة',humidity+'٪'),metric('الحالة',info[1]));
      const hours=document.createElement('span');hours.className='day-hours';indexes.filter((_,i)=>i%3===0).forEach(i=>{const hour=document.createElement('span');hour.className='day-hour';const time=document.createElement('span'),temp=document.createElement('strong');time.textContent=clock(hourly.time[i]);temp.textContent=degrees(hourly.temperature_2m[i]);hour.append(time,temp);hours.append(hour)});
      detail.append(stats,hours);button.append(name,stamp,icon,desc,range,detail);button.setAttribute('aria-label',name.textContent+'، '+info[1]+'، العظمى '+degrees(daily.temperature_2m_max[index])+'، الصغرى '+degrees(daily.temperature_2m_min[index]));
      button.addEventListener('focus',()=>{if(index!==selected)select(index,false)});button.addEventListener('click',()=>select(index,true));strip.append(button);
    });select(0,true);
  }
  function navigate(direction){if(!data)return;const active=document.activeElement;if(active===$('cellHomeButton')){if(direction==='down')select(selected,true);return}if(direction==='up')$('cellHomeButton').focus({preventScroll:true});else if(direction==='left')select(selected+1,true);else if(direction==='right')select(selected-1,true)}
  window.handleAndroidDpad=navigate;window.handleAndroidOk=()=>document.activeElement?.click();window.handleAndroidBack=()=>window.closeZikraCell?.();
  document.addEventListener('keydown',event=>{const direction={ArrowLeft:'left',ArrowRight:'right',ArrowUp:'up',ArrowDown:'down'}[event.key];if(direction){event.preventDefault();event.stopImmediatePropagation();navigate(direction)}},true);
  $('cellHomeButton').addEventListener('click',()=>window.handleAndroidBack());
  async function load(){try{const lat=Number(localStorage.getItem('zikra_lat')||50.9848),lon=Number(localStorage.getItem('zikra_lon')||11.0299);$('place').textContent=localStorage.getItem('zikra_city')||'إرفورت';const url='https://api.open-meteo.com/v1/forecast?latitude='+lat+'&longitude='+lon+'&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max,sunrise,sunset&hourly=temperature_2m,relative_humidity_2m&timezone=auto&forecast_days=10';const result=await fetch(url);if(!result.ok)throw Error('weather');data=await result.json();$('date').textContent=new Intl.DateTimeFormat(locale,{dateStyle:'full',timeZone:data.timezone}).format(new Date());build();$('weatherStatus').textContent='المواقيت بحسب '+data.timezone+' · توقعات عشرة أيام'}catch(_){$('weatherStatus').textContent='تعذّر جلب الطقس. تحقق من الإنترنت ثم افتح الخلية مجددًا.'}}
  load();
})();
