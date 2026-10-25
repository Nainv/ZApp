(() => {
 'use strict';const $=id=>document.getElementById(id),card=$('memoryCard');let items=[],index=0,request=0;
 function render(){if(!items.length)return;const m=items[index%items.length];$('memoryPreviewText').textContent=m.text;$('memoryPreviewText').dir=ZikraI18n.language==='ar'?'rtl':'ltr';$('memoryPreviewSource').textContent=m.title+(m.source?' · '+m.source:'');$('memoryPreviewBenefit').textContent=(m.reflection||m.title)+(m.lesson?' '+m.lesson:'');$('counter').textContent=(index+1)+' / '+items.length;card.setAttribute('aria-label',m.title);ZikraFit.memory(card);}
 async function load(){const id=++request;try{const result=await ZikraContent.load();if(id!==request)return;items=result;index%=items.length;render()}catch(_){$('memoryPreviewText').textContent=ZikraI18n.language==='ar'?'تعذّر تحميل الذكر':ZikraI18n.language==='de'?'Inhalt nicht verfügbar':'Content unavailable'}}
 const move=step=>{if(!items.length)return;index=(index+step+items.length)%items.length;render()};
 window.zikraRemoteDelegate={move(direction){if(document.activeElement===card){if(direction===(ZikraI18n.language==='ar'?'right':'left'))move(-1);else if(direction===(ZikraI18n.language==='ar'?'left':'right'))move(1);else if(direction==='up')$('cellHomeButton').focus({preventScroll:true});return true}if(direction==='down'){card.focus({preventScroll:true});return true}return false},ok(){return document.activeElement===card}};
 window.addEventListener('message',event=>{if(event.source!==parent)return;const p=event.data?.preferences;if(p?.language&&p.language!==ZikraI18n.language){ZikraI18n.set(p.language);load()}});
 if(parent!==window)parent.postMessage({type:'zikra:context-request'},'*');load();
})();
