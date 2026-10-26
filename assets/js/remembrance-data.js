/* Local content bundles load only for the selected language, including from file://. */
(() => {
 'use strict';const base=document.currentScript.src,pending=new Map();
 window.ZikraContent={load(language=ZikraI18n.language){const lang=['ar','en','de'].includes(language)?language:'ar';if(window.ZIKRA_CONTENT?.[lang])return Promise.resolve(window.ZIKRA_CONTENT[lang]);if(pending.has(lang))return pending.get(lang);const promise=new Promise((resolve,reject)=>{const script=document.createElement('script');script.src=new URL('../data/remembrance-'+lang+'.js?v=229',base).href;script.onload=()=>resolve(window.ZIKRA_CONTENT[lang]);script.onerror=()=>{pending.delete(lang);script.remove();reject(Error('Content unavailable'))};document.head.append(script)});pending.set(lang,promise);return promise}};
})();
