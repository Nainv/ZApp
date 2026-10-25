/* One home document owns preferences and prayer data. Cached cells receive snapshots. */
(() => {
  'use strict';
  const routes={settings:'cells/settings/index.html?cell=1',weather:'cells/weather/index.html',prayer:'cells/prayer/index.html',zikra:'cells/zikra/index.html'};
  const frames=new Map();let overlay=null,frame=null,returnFocus=null,openingMemory=null;
  const read=(key,fallback)=>localStorage.getItem(key)??fallback;
  function preferences(){let prayers={};try{prayers=JSON.parse(read('zikra_adhan_settings_v1','{}'))}catch(_){}return {...ZikraLocation.read(),language:read('zikra_language','ar'),backgroundAdhan:read('zikra_adhan_background',String(window.ZikraRuntime?.state.adhanBackground??true))!=='false',autostart:read('zikra_autostart',String(window.ZikraRuntime?.state.autostart??false))==='true',prayers}}
  function context(){return {preferences:preferences(),prayer:window.zikraPrayerSnapshot?.()||{zone:prayerTimeZone,date:prayerDataDate,timings:prayerTimesObj,schedule:prayerSchedule},weather:weatherData,runtime:{...window.ZikraRuntime?.state,timeEpoch:window.ZikraClock?.now(),clockSynced:window.ZikraClock?.synced},adhanActive:window.zikraAdhanActive||null}}
  function send(target,type='zikra:context'){const data=context();data.memory=type==='zikra:session-start'&&target?.dataset.cell==='zikra'?openingMemory:window.zikraCurrentMemory?.();target?.contentWindow?.postMessage({type,...data},'*')}
  window.zikraBroadcastContext=()=>frames.forEach(target=>send(target));
  window.addEventListener('zikra:app-update',event=>frames.get('settings')?.contentWindow?.postMessage({type:'zikra:app-update-state',state:event.detail},'*'));
  function ensureOverlay(){if(overlay)return;overlay=document.createElement('div');overlay.id='zikraCellOverlay';overlay.hidden=true;overlay.style.cssText='position:fixed;inset:0;z-index:30000;background:#101b23;overflow:hidden';document.body.append(overlay)}
  function openCell(id,trigger){
    if(id==='quran'){openModal('quranModal');return}
    if(!routes[id])return;if(id==='zikra')openingMemory=window.zikraCurrentMemory?.();ensureOverlay();
    returnFocus=trigger?.matches('button,[tabindex]')?trigger:trigger?.querySelector('button,[tabindex]')||document.activeElement;
    document.querySelector('.app').inert=true;document.body.classList.remove('remote-focus-active');document.body.classList.add('cell-open');
    overlay.hidden=false;
    if(frame){frame.hidden=true;frame.removeAttribute('id');frame.contentWindow?.postMessage({type:'zikra:cell-active',active:false},'*')}
    frame=frames.get(id);
    if(!frame){
      frame=document.createElement('iframe');frames.set(id,frame);frame.dataset.cell=id;frame.title=({settings:'الإعدادات',weather:'الطقس',prayer:'مواقيت الصلاة',zikra:'الأذكار'})[id];frame.setAttribute('allow','geolocation');frame.style.cssText='display:block;width:100%;height:100%;border:0;background:#101b23;';
      const target=frame;target.addEventListener('load',()=>{target.dataset.ready='true';if(target===frame&&!overlay.hidden){send(target,'zikra:session-start');target.contentWindow?.postMessage({type:'zikra:cell-active',active:true},'*');target.contentWindow?.focus();target.contentWindow?.postMessage({type:'zikra:cell-focus'},'*')}});
      frame.src=routes[id];overlay.append(frame);
    }
    frames.delete(id);frames.set(id,frame);while(frames.size>2){const [oldId,oldFrame]=frames.entries().next().value;if(oldFrame===frame)break;oldFrame.remove();frames.delete(oldId)}
    frame.id='zikraCellFrame';frame.hidden=false;frame.focus({preventScroll:true});
    if(frame.dataset.ready==='true'){send(frame,'zikra:session-start');frame.contentWindow?.postMessage({type:'zikra:cell-active',active:true},'*');frame.contentWindow?.focus()}
  }
  function closeCell(){
    window.zikraPreviewAdhan?.(null);
    if(!overlay||overlay.hidden)return false;frame.contentWindow?.postMessage({type:'zikra:cell-active',active:false},'*');frame.hidden=true;overlay.hidden=true;
    document.querySelector('.app').inert=false;document.body.classList.remove('cell-open');document.body.classList.add('remote-focus-active');
    returnFocus?.isConnected&&returnFocus.focus({preventScroll:true});returnFocus=null;return true;
  }
  window.zikraCloseCellOverlay=closeCell;
  window.zikraClearInactiveCells=()=>{for(const [id,target] of frames){if(overlay&&!overlay.hidden&&target===frame)continue;target.remove();frames.delete(id);if(target===frame)frame=null}};
  function back(){if(overlay&&!overlay.hidden){frame.contentWindow?.postMessage({type:'zikra:cell-back'},'*');return}window.zikraHomeBack?.()}
  window.zikraBackFromRemote=back;
  function savePreferences(data){
    if(!data||!['ar','de','en'].includes(data.language)||typeof data.city!=='string'||!data.city.trim())throw Error('إعدادات الموقع غير صالحة.');
    const voices=['makkah','madinah','alafasy','syria'];const prayers={};for(const key of ['Fajr','Dhuhr','Asr','Maghrib','Isha']){const item=data.prayers?.[key];prayers[key]={enabled:item?.enabled!==false,voice:voices.includes(item?.voice)?item.voice:'makkah'}}
    ZikraLocation.save(data);localStorage.setItem('zikra_adhan_background',String(data.backgroundAdhan!==false));localStorage.setItem('zikra_autostart',String(data.autostart));window.AndroidTV?.setStartup?.(!!data.autostart,data.language);localStorage.setItem('zikra_language',data.language);localStorage.setItem('zikra_adhan_settings_v1',JSON.stringify(prayers));
    window.zikraApplySavedSettings?.();window.zikraBroadcastContext();
  }
  window.addEventListener('message',event=>{
    if(!frame||event.source!==frame.contentWindow||!event.data||overlay.hidden)return;
    const message=event.data;
    if(message.type==='zikra:cell-close')closeCell();
    else if(message.type==='zikra:adhan-preview'&&frame.dataset.cell==='settings')window.zikraPreviewAdhan?.(message.voice||null);
    else if(message.type==='zikra:context-request')send(frame);
    else if(message.type==='zikra:settings-save'&&frame.dataset.cell==='settings'){
      try{savePreferences(message.preferences);frame.contentWindow?.postMessage({type:'zikra:settings-saved',requestId:message.requestId},'*')}catch(error){frame.contentWindow?.postMessage({type:'zikra:settings-error',requestId:message.requestId,message:error.message},'*')}
    }
  });
  document.addEventListener('click',event=>{
    if(event.target.closest('#miniPrev,#miniPlay,#miniNext'))return;
    const target=event.target.closest('[data-cell],#settingsBtn,#datePanel,.date-weather-widget,.prayer-strip,.prayer-strip .prayer,#memoryCard,.player,#openQuranFromPlayer');if(!target)return;
    const id=target.dataset.cell||(target.matches('#settingsBtn')?'settings':target.matches('#datePanel,.date-weather-widget')?'weather':target.matches('.prayer-strip,.prayer-strip .prayer')?'prayer':target.matches('#memoryCard')?'zikra':'quran');
    event.preventDefault();event.stopImmediatePropagation();openCell(id,target);
  },true);
})();
