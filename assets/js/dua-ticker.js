/* Compositor animations; no animation-frame script or per-frame layout reads. */
function mountDuaTicker(content) {
  'use strict';window.zikraDuaTicker?.destroy();
  const track=document.getElementById('duaTrack'),viewport=track?.parentElement;if(!track||!viewport)return;
  track.replaceChildren();const mirrored=ZikraI18n.language!=='ar';track.style.transform=mirrored?'scaleX(-1)':'none';const source=[...new Set((content.map(item=>item.text)).filter(v=>typeof v==='string'&&v.trim()).map(v=>v.trim()))];if(!source.length)return;
  const speed=31.5,gap=34;let entries=[],next=0,width=0,stopped=false,paused=false;
  const position=item=>item.start+Number(item.animation.currentTime||0)/1000*speed;
  function append(right){
    const element=document.createElement('div');element.className='dua-entry';element.setAttribute('aria-hidden','true');const text=document.createElement('span'),mark=document.createElement('span');text.className='dua-item';text.dir=ZikraI18n.language==='ar'?'rtl':'ltr';text.style.direction=text.dir;text.style.transform=mirrored?'scaleX(-1)':'none';mark.style.transform=mirrored?'scaleX(-1)':'none';text.textContent=source[next++%source.length];mark.className='dua-separator';mark.textContent='۞';element.append(text,mark);track.append(element);
    const size=Math.ceil(element.getBoundingClientRect().width),start=right-size,end=width+gap,duration=(end-start)/speed*1000;
    const animation=element.animate([{transform:'translate3d('+start+'px,-50%,0)'},{transform:'translate3d('+end+'px,-50%,0)'}],{duration,easing:'linear',fill:'forwards'}),item={element,start,animation};entries.push(item);if(paused)animation.pause();
    animation.onfinish=()=>{if(stopped)return;element.remove();entries=entries.filter(e=>e!==item);fill()};return start;
  }
  function fill(){if(stopped||!width)return;let left=entries.length?Math.min(...entries.map(position)):width;while(left>-width&&entries.length<60)left=append(left-gap)}
  function layout(){if(stopped)return;entries.forEach(item=>{item.animation.cancel();item.element.remove()});entries=[];width=viewport.clientWidth;fill()}
  function suspend(){const nextPaused=document.hidden||document.body.classList.contains('cell-open')||!!document.querySelector('.modal-backdrop.open');if(nextPaused===paused)return;paused=nextPaused;entries.forEach(item=>paused?item.animation.pause():item.animation.play())}
  const resize=typeof ResizeObserver==='function'?new ResizeObserver(layout):null;resize?.observe(viewport);if(!resize)window.addEventListener('resize',layout,{passive:true});
  const state=new MutationObserver(suspend);state.observe(document.body,{attributes:true,attributeFilter:['class']});document.querySelectorAll('.modal-backdrop').forEach(modal=>state.observe(modal,{attributes:true,attributeFilter:['class']}));document.addEventListener('visibilitychange',suspend);
  window.zikraDuaTicker={destroy(){stopped=true;entries.forEach(item=>item.animation.cancel());resize?.disconnect();state.disconnect();document.removeEventListener('visibilitychange',suspend);window.removeEventListener('resize',layout);track.replaceChildren()}};
  Promise.resolve(document.fonts?.ready).then(()=>{if(!stopped){layout();suspend()}});
}
let tickerRequest=0;async function refreshDuaTicker(){const id=++tickerRequest;try{const content=await ZikraContent.load();if(id===tickerRequest)mountDuaTicker(content)}catch(_){}}window.addEventListener('zikra:language',refreshDuaTicker);refreshDuaTicker();
