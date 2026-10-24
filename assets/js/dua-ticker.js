/* One measured lane: every prayer owns its full width and a separate Zikra mark. */
(() => {
  'use strict';
  window.zikraDuaTicker?.destroy();
  const track = document.getElementById('duaTrack');
  const viewport = track?.parentElement;
  if (!track || !viewport) return;
  track.replaceChildren();
  const source = [...new Set((window.ZIKRA_DUAS_AR || []).filter(value => typeof value === 'string' && value.trim()).map(value => value.trim()))];
  if (!source.length) return;
  const speed = 31.5, gap = 34;
  let entries = [], next = 0, frame = 0, previousTime = 0, stopped = false;
  function addBefore(rightEdge) {
    const element = document.createElement('div');element.className = 'dua-entry';element.setAttribute('aria-hidden','true');
    const text = document.createElement('span');text.className = 'dua-item';text.dir = 'rtl';text.textContent = source[next++ % source.length];
    const separator = document.createElement('span');separator.className = 'dua-separator';separator.textContent = '۞';
    element.append(text,separator);track.append(element);
    const width = Math.ceil(element.getBoundingClientRect().width);
    const item = {element,width,x:rightEdge-width};entries.push(item);
    element.style.transform = 'translate3d('+item.x+'px,-50%,0)';
    return item;
  }
  function fill() {
    const width = viewport.clientWidth;
    if (!width) return;
    if (!entries.length) addBefore(width);
    let left = Math.min(...entries.map(item=>item.x));
    while (left > -width && entries.length < 60) left = addBefore(left-gap).x;
  }
  function layout() { if(stopped)return;entries.forEach(item=>item.element.remove());entries=[];previousTime=0;fill(); }
  function animate(time) {
    if (stopped) return;
    const seconds = previousTime && !document.hidden ? Math.min(50,time-previousTime)/1000 : 0;previousTime=time;
    const width = viewport.clientWidth;
    entries = entries.filter(item=>{item.x+=seconds*speed;if(item.x>width+gap){item.element.remove();return false}item.element.style.transform='translate3d('+item.x+'px,-50%,0)';return true});
    fill();frame=requestAnimationFrame(animate);
  }
  const observer = typeof ResizeObserver==='function'?new ResizeObserver(layout):null;
  observer?.observe(viewport);
  if (!observer) window.addEventListener('resize',layout);
  window.zikraDuaTicker = {destroy(){stopped=true;cancelAnimationFrame(frame);observer?.disconnect();window.removeEventListener('resize',layout);track.replaceChildren()}};
  Promise.resolve(document.fonts?.ready).then(()=>{if(!stopped){layout();frame=requestAnimationFrame(animate)}});
})();
