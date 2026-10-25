/* One local, user-triggered Basmala/dua clip; never autoplay and never mix it with Quran/adhan. */
(() => {
 'use strict';const button=document.getElementById('brandDuaBtn'),quran=document.getElementById('audio');if(!button||!quran)return;
 const clip=new Audio();clip.preload='none';clip.src='assets/audio/brand-dua.mp3';let active=false,restore=false,request=0;
 function render(){button.setAttribute('aria-pressed',String(active));button.setAttribute('aria-label',ZikraI18n.t(active?'إيقاف الدعاء':'الاستماع إلى البسملة والدعاء'));}
 function stop(resume=true){if(!active)return false;active=false;request++;clip.pause();try{clip.currentTime=0}catch(_){}const shouldResume=restore;restore=false;render();if(resume&&shouldResume&&!window.zikraAdhanActive&&!document.hidden)quran.play().catch(()=>{});return true;}
 async function play(){if(active){stop();return}if(window.zikraAdhanActive)return;const id=++request;restore=!quran.paused;quran.pause();active=true;render();try{await clip.play()}catch(_){if(id===request)stop();}}
 button.addEventListener('click',play);clip.addEventListener('ended',()=>stop());clip.addEventListener('error',()=>stop());quran.addEventListener('play',()=>{if(active)stop(false)});window.addEventListener('zikra:language',render);window.addEventListener('pagehide',()=>stop(false));document.addEventListener('visibilitychange',()=>{if(document.hidden)stop(false)});
 window.zikraStopBrandDua=stop;render();
})();
