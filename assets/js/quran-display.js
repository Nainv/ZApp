/* Presentation only. Original Quran text, verse numbers and recording timings stay unchanged. */
(() => {
 'use strict';
 const prefix='بِسۡمِ ٱللَّهِ ٱلرَّحۡمَـٰنِ ٱلرَّحِیمِ',ornament='﴿ بــســمِ اللهِ الــرحــمــنِ الــرحــيــم ﴾';
 function opening(surah,ayah,text){const source=String(text||''),trimmed=source.trim();if(Number(ayah)!==1||Number(surah)===9)return {text:source,basmala:false,numbered:false};if(Number(surah)===1)return {text:'',basmala:true,numbered:true};return {text:trimmed.startsWith(prefix)?trimmed.slice(prefix.length).trimStart():source,basmala:true,numbered:false};}
 const api={opening,ornament};if(typeof module!=='undefined'&&module.exports)module.exports=api;if(typeof window!=='undefined')window.ZikraQuranDisplay=api;
})();
