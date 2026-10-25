/* Refresh presentation and disposable resources while keeping the live player and saved data. */
(() => {
 'use strict';const button=document.getElementById('homeRefreshBtn');let busy=false;
 async function refresh(){if(busy)return;busy=true;button?.setAttribute('aria-busy','true');button?.classList.add('refreshing');
  try{
   window.AndroidTV?.refreshInterfaceCache?.();window.zikraClearInactiveCells?.();
   if(window.caches)try{const names=await caches.keys();await Promise.all(names.filter(name=>/^zikra-(?:ui|shell)-/.test(name)).map(name=>caches.delete(name)))}catch(_){}
   await Promise.all([...document.querySelectorAll('link[rel=stylesheet]')].filter(link=>link.href.includes('/assets/css/')).map(link=>new Promise(resolve=>{const next=link.cloneNode();const url=new URL(link.href);url.searchParams.set('refresh',String(Date.now()));next.href=url.href;let finished=false;const done=success=>{if(finished)return;finished=true;clearTimeout(timer);if(success)link.remove();else next.remove();resolve()};const timer=setTimeout(()=>done(false),5000);next.onload=()=>done(true);next.onerror=()=>done(false);link.after(next)})));
   window.zikraRenderInterface?.();window.ZikraNavigation?.invalidate();
   const status=document.getElementById('homeUpdateStatus');if(status){status.textContent=ZikraI18n.t('تم تحديث الواجهة مع الحفاظ على بياناتك والتلاوة.');status.hidden=false;setTimeout(()=>status.hidden=true,4000)}
  }finally{busy=false;button?.removeAttribute('aria-busy');button?.classList.remove('refreshing')}
 }
 window.zikraRefreshInterface=refresh;button?.addEventListener('click',refresh);
})();
