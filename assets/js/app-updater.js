/* Android validates and installs updates. Browsers can only download the APK. */
(() => {
  'use strict';const t=value=>ZikraI18n.t(value);
  const button=document.getElementById('updateApp'),label=document.getElementById('updateAppLabel'),status=document.getElementById('updateAppStatus'),progress=document.getElementById('updateAppProgress');
  if(!button)return;
  const active=new Set(['saving','checking','downloading','verifying','permission','installing','ready']);let busy=false;
  function render(state){
    if(!state||typeof state.phase!=='string')return;
    const messages=document.getElementById('updateMessages');if(messages)messages.hidden=state.phase==='idle';label.hidden=state.phase==='idle';
    busy=active.has(state.phase);button.disabled=busy;button.setAttribute('aria-busy',String(busy));
    label.textContent=state.phase==='idle'?'أحدث نسخة متوفرة على ذكرى':state.message||'تحديث التطبيق';
    status.hidden=state.phase==='idle';status.textContent=state.phase==='permission'||state.phase==='installing'?'قد يطلب أندرويد موافقتك. اضغط «فتح» إذا لم يرجع التطبيق تلقائيًا.':state.phase==='downloaded'?'المتصفح ينزّل الملف فقط. افتحه على جهاز أندرويد لتثبيت النسخة الجديدة.':state.phase==='error'?state.message:'التحديث يحافظ على بيانات التطبيق عند تثبيته فوق النسخة الحالية.';
    label.textContent=t(label.textContent);status.textContent=t(status.textContent);
    progress.hidden=!['checking','downloading','verifying'].includes(state.phase);
    if(Number.isFinite(state.progress)&&state.progress>=0)progress.value=state.progress;else progress.removeAttribute('value');
  }
  function restore(){try{if(window.AndroidTV?.getUpdateState)render(JSON.parse(window.AndroidTV.getUpdateState()))}catch(_){render({phase:'error',message:'تعذّر قراءة حالة التحديث. حاول مجددًا.'})}}
  async function downloadInBrowser(){
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),20000);
    try{
      const response=await fetch('https://api.github.com/repos/koukach91-hash/zikra-android-tv-new/releases/latest?t='+Date.now(),{headers:{Accept:'application/vnd.github+json'},signal:controller.signal});
      if(!response.ok)throw Error(response.status===403||response.status===429?'وصل البحث إلى حد ذكرى المؤقت. حاول لاحقًا.':'تعذّر الوصول إلى آخر إصدار.');
      const release=await response.json(),apk=release.assets?.find(asset=>asset.name==='app-release.apk');
      if(release.draft||release.prerelease||!apk)throw Error('لا يوجد ملف APK مستقر متاح في الإصدار الأخير.');
      const url=new URL(apk.browser_download_url);
      if(url.protocol!=='https:'||url.hostname!=='github.com'||!url.pathname.startsWith('/koukach91-hash/zikra-android-tv-new/releases/download/'))throw Error('رابط التحديث غير صالح.');
      const link=document.createElement('a');link.href=url.href;link.download='zikra-update.apk';link.rel='noopener';document.body.append(link);link.click();link.remove();
      render({phase:'downloaded',message:'تم طلب تنزيل أحدث نسخة من ذكرى.'});
    }finally{clearTimeout(timer)}
  }
  button.addEventListener('click',async()=>{
    if(busy)return;render({phase:'saving',message:'جارٍ حفظ الإعدادات قبل التحديث…'});
    try{await window.zikraSaveForUpdate?.();render({phase:'checking',message:'جارٍ البحث عن أحدث إصدار…'});if(window.AndroidTV?.updateApp){window.AndroidTV.updateApp();restore()}else await downloadInBrowser()}
    catch(error){render({phase:'error',message:error.name==='AbortError'?'استغرق الاتصال وقتًا طويلًا. حاول مجددًا.':error.message||'تعذّر التحديث. تحقق من الإنترنت.'})}
  });
  window.addEventListener('zikra:app-update',event=>render(event.detail));
  window.addEventListener('message',event=>{if(event.source!==parent)return;if(event.data?.type==='zikra:app-update-state')render(event.data.state);else if(event.data?.type==='zikra:session-start')restore()});
  restore();
})();
