/* Lazy local Quran text: no page/index request and no work on home key presses. */
(() => {
 'use strict';const base=document.currentScript.src;let pending=null;
 function load(){if(window.ZIKRA_QURAN_TEXT?.surahs?.length===114)return Promise.resolve(window.ZIKRA_QURAN_TEXT);if(pending)return pending;pending=new Promise((resolve,reject)=>{const script=document.createElement('script');script.src=new URL('../data/quran-text.js?v=221',base).href;script.onload=()=>{if(window.ZIKRA_QURAN_TEXT?.surahs?.length===114)resolve(window.ZIKRA_QURAN_TEXT);else{pending=null;script.remove();reject(Error('تعذّر تحميل نص المصحف المحلي.'))}};script.onerror=()=>{pending=null;script.remove();reject(Error('تعذّر تحميل نص المصحف المحلي.'))};document.head.append(script)});return pending}
 window.ZikraQuranData={load};
})();
