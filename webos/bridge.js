/* LG adapter loaded before the shared UI, in both bundled and hosted pages. */
(() => {
 'use strict';
 document.documentElement.classList.add('tv-device');
 const framed=parent!==window;
 const isRoot=framed&&new URLSearchParams(location.search).get('lg-shell')==='1';
 const owns=key=>key.startsWith('zikra_')||['zikraSettings','quran_bookmark','played_surahs'].includes(key);
 const snapshot=()=>Object.fromEntries(Object.keys(localStorage).filter(owns).map(key=>[key,localStorage.getItem(key)]));
 function send(command,extra={}){if(framed)parent.postMessage({type:'zikra-lg',command,...extra},'*');}
 function persist(){if(isRoot)send('storage',{values:snapshot()});else if(framed)send('persist');}
 // Include nested settings writes as well as playback/bookmark writes.
 const original={};
 for(const method of ['setItem','removeItem','clear']){original[method]=Storage.prototype[method];Storage.prototype[method]=function(...args){const result=original[method].apply(this,args);if(this===localStorage)persist();return result;};}
 function refresh(){persist();if(isRoot)send('refresh');else if(framed)send('refresh');else location.reload();}
 window.ZikraWebOS={exit:()=>send('exit'),refresh,unavailable:name=>{
  const language=localStorage.getItem('zikra_language')||'ar';
  const text=name==='startup'?{ar:'الفتح التلقائي عند تشغيل الشاشة غير متاح في نسخة LG الحالية.',en:'Automatic launch at TV startup is unavailable in this LG version.',de:'Automatischer Start ist in dieser LG-Version nicht verfügbar.'}:{ar:'الأذان متاح أثناء فتح ذكرى. لا يتوفر الأذان بعد إغلاق التطبيق في نسخة LG الحالية.',en:'Adhan works while Zikra is open. Background adhan is unavailable in this LG version.',de:'Adhan funktioniert bei geöffneter App. Hintergrund-Adhan ist in dieser LG-Version nicht verfügbar.'};
  const node=document.getElementById('toast');if(node){node.textContent=text[language]||text.ar;node.hidden=false;setTimeout(()=>node.hidden=true,7000);}
 }};
 window.addEventListener('message',event=>{
  if(event.data?.type!=='zikra-lg')return;
  const children=[...document.querySelectorAll('iframe')];
  if(!children.some(frame=>frame.contentWindow===event.source))return;
  if(event.origin!==location.origin)return;
  if(event.data.command==='persist')persist();
  else if(['refresh','exit'].includes(event.data.command))send(event.data.command);
 });
 window.addEventListener('pagehide',persist);
 window.addEventListener('storage',persist);
 window.addEventListener('keydown',event=>{
  const command={415:'play',19:'pause',413:'stop',417:'next',412:'previous'}[event.keyCode];
  if(command){event.preventDefault();window.handleAndroidMediaKey?.(command);}
 },true);
 document.addEventListener('visibilitychange',()=>{if(document.hidden){persist();window.zikraStopAdhan?.();document.querySelectorAll('audio').forEach(audio=>audio.pause());}});
 async function start(){
  if(isRoot)await new Promise(resolve=>{
   const timer=setTimeout(()=>{window.removeEventListener('message',receive);resolve();},5000);
   function receive(event){if(event.source!==parent||event.data?.type!=='zikra-lg-state')return;clearTimeout(timer);window.removeEventListener('message',receive);try{for(const key of Object.keys(localStorage).filter(owns))original.removeItem.call(localStorage,key);for(const[key,value]of Object.entries(event.data.values||{}))if(owns(key)&&typeof value==='string')original.setItem.call(localStorage,key,value);}catch(_){}resolve();}
   window.addEventListener('message',receive);send('state-request');
  });
  for(const old of document.querySelectorAll('script[type="text/zikra-script"]')){
   const script=document.createElement('script');
   if(old.hasAttribute('src')){script.src=old.getAttribute('src');await new Promise((resolve,reject)=>{script.onload=resolve;script.onerror=reject;old.replaceWith(script);});}
   else{script.textContent=old.textContent;old.replaceWith(script);}
  }
  document.dispatchEvent(new Event('DOMContentLoaded'));
  window.ZikraI18n?.apply();
  persist();
  const status=document.getElementById('updateAppLabel');
  if(status)status.textContent='LG · تُحمّل أحدث واجهة منشورة عند فتح التطبيق';
  const notice=document.getElementById('updateNotice');if(notice)notice.hidden=true;
  if(isRoot)send('ready');
 }
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',()=>start().catch(console.error),{once:true});else start().catch(console.error);
})();
