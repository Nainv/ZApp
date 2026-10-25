/* Refresh presentation and disposable resources while keeping the live player and saved data. */
(() => {
 'use strict';const button=document.getElementById('homeRefreshBtn');let busy=false;
 function spin(){const icon=button?.querySelector('.refresh-icon');if(!icon)return Promise.resolve();return new Promise(resolve=>{let timer,animation;const done=()=>{clearTimeout(timer);icon.style.animation='';resolve()};try{if(icon.animate){animation=icon.animate([{transform:'rotate(0deg)'},{transform:'rotate(1080deg)'}],{duration:600,easing:'ease-in-out'});animation.onfinish=done;animation.oncancel=done;}else icon.style.animation='zikra-refresh-spin 200ms linear 3';}catch(_){}timer=setTimeout(done,650)})}
 async function refresh(){if(busy)return;busy=true;button?.setAttribute('aria-busy','true');const spinning=spin();
  try{
   window.AndroidTV?.checkForUpdate?.();window.ZikraRuntime?.refresh();window.AndroidTV?.refreshInterfaceCache?.();window.zikraClearInactiveCells?.();
   if(window.caches)try{const names=await caches.keys();await Promise.all(names.filter(name=>/^zikra-(?:ui|shell)-/.test(name)).map(name=>caches.delete(name)))}catch(_){}
   await Promise.all([...document.querySelectorAll('link[rel=stylesheet]')].filter(link=>link.href.includes('/assets/css/')).map(link=>new Promise(resolve=>{const next=link.cloneNode();const url=new URL(link.href);url.searchParams.set('refresh',String(Date.now()));next.href=url.href;let finished=false;const done=success=>{if(finished)return;finished=true;clearTimeout(timer);if(success)link.remove();else next.remove();resolve()};const timer=setTimeout(()=>done(false),5000);next.onload=()=>done(true);next.onerror=()=>done(false);link.after(next)})));
   window.zikraRenderInterface?.();window.ZikraNavigation?.invalidate();
  }finally{await spinning;busy=false;button?.removeAttribute('aria-busy');if(!document.querySelector('.app')?.inert)document.getElementById('brandDuaBtn')?.focus({preventScroll:true})}
 }
 window.zikraRefreshInterface=refresh;button?.addEventListener('click',refresh);
})();
