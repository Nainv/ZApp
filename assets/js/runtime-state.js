/* Monotonic app time. Synchronization never changes Android's system settings. */
(() => {
 'use strict';
 try{if(window.AndroidTV?.isTelevision?.())document.documentElement.classList.add('tv-device');if(window.navigator?.standalone===true)document.documentElement.classList.add('standalone-device')}catch(_){}let state={},anchor=Date.now(),monotonic=performance.now(),synced=false,lastRead=0;
 function apply(value){if(!value)return;state=value;if(value.clockSynced===true&&Number.isFinite(value.timeEpoch)&&value.timeEpoch>1577836800000){anchor=value.timeEpoch;monotonic=performance.now();synced=true}const badge=document.getElementById('updateNotice');if(badge){const hadFocus=document.activeElement===badge;const available=value.updateAvailable===true&&Number(value.installedVersion)>0&&Number(value.updateVersion)>Number(value.installedVersion);if(badge.hidden===available)badge.hidden=!available;const label=ZikraI18n.t('يتوفر تحديث جديد');if(badge.textContent!==label)badge.textContent=label;if(!available&&hadFocus)document.getElementById('settingsBtn')?.focus({preventScroll:true})}window.dispatchEvent(new CustomEvent('zikra:runtime',{detail:value}))}
 function refresh(){try{if(window.AndroidTV?.getRuntimeState)apply(JSON.parse(window.AndroidTV.getRuntimeState()));else if(parent!==window)parent.postMessage({type:'zikra:context-request'},'*')}catch(_){}lastRead=Date.now()}
 window.ZikraClock={now:()=>synced?anchor+performance.now()-monotonic:Date.now(),get synced(){return synced}};
 window.ZikraRuntime={refresh,get state(){return state}};
 window.addEventListener('zikra:runtime-state',event=>apply(event.detail));
 document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible')refresh()});
 window.addEventListener('online',()=>{window.AndroidTV?.checkForUpdate?.();refresh()});
 window.addEventListener('message',event=>{if(event.source!==parent)return;if(event.data?.runtime)apply(event.data.runtime)});
 window.addEventListener('focus',()=>{if(Date.now()-lastRead>60000){window.AndroidTV?.checkForUpdate?.();refresh()}});
 window.addEventListener('zikra:language',()=>{const badge=document.getElementById('updateNotice');if(badge)badge.textContent=ZikraI18n.t('يتوفر تحديث جديد')});
 // This script is loaded in <head>; the badge does not exist until the body is parsed.
 function bindUpdateNotice(){
  const updateNotice=document.getElementById('updateNotice');
  if(!updateNotice)return;
  updateNotice.addEventListener('click',()=>{
   if(updateNotice.hidden||window.zikraUpdateBusy||!window.AndroidTV?.updateApp)return;
   window.dispatchEvent(new CustomEvent('zikra:app-update',{detail:{phase:'checking',message:'جارٍ البحث عن أحدث إصدار…',progress:-1}}));
   try{window.AndroidTV.updateApp()}catch(_){window.dispatchEvent(new CustomEvent('zikra:app-update',{detail:{phase:'error',message:'تعذّر بدء التحديث. حاول مجددًا.',progress:-1}}))}
  });
  refresh();
 }
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',bindUpdateNotice,{once:true});else bindUpdateNotice();
 window.AndroidTV?.checkForUpdate?.();refresh();setTimeout(refresh,2500);setTimeout(refresh,12000);
 let messageTimer;window.addEventListener('zikra:app-update',event=>{const node=document.getElementById('homeUpdateStatus');if(!node)return;const update=event.detail||{};window.zikraUpdateBusy=['checking','downloading','verifying','permission','ready','installing'].includes(update.phase);clearTimeout(messageTimer);node.textContent=ZikraI18n.t(update.message||'تحديث التطبيق');node.hidden=update.phase==='idle';if(!window.zikraUpdateBusy)messageTimer=setTimeout(()=>node.hidden=true,8000)});
})();
