/* Fit the current remembrance only, after content changes or a resize. */
(() => {
 'use strict';let pending=0;
 function fit(node,box,max,min){if(!node||!box)return;node.style.fontSize=max+'px';for(let size=max;size>min&&node.scrollHeight>box.clientHeight+1;){size=Math.max(min,size-2);node.style.fontSize=size+'px'}}
 function memory(card){if(!card||!card.clientHeight)return;const inner=document.body.classList.contains('cell-zikra'),width=card.clientWidth;
  const text=card.querySelector('.memory-preview-text'),source=card.querySelector('.memory-preview-source'),benefit=card.querySelector('.memory-preview-benefit');
  if(source)source.style.fontSize=(inner?Math.max(13,Math.min(18,width/42)):Math.max(11,Math.min(15,width/36)))+'px';
  if(benefit)benefit.style.fontSize=(inner?Math.max(14,Math.min(20,width/40)):Math.max(11,Math.min(15,width/37)))+'px';
  fit(text,card.querySelector('.memory-center'),inner?Math.min(38,width/20):Math.min(27,width/22),inner?14:13);
 }
 function all(){cancelAnimationFrame(pending);pending=requestAnimationFrame(()=>document.querySelectorAll('.tile-memory').forEach(memory))}
 window.ZikraFit={memory,all};window.addEventListener('resize',all,{passive:true});document.fonts?.ready.then(all);if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',all,{once:true});else all();
})();
