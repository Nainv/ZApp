(function(){
'use strict';
const track=document.getElementById('duaTrack');
const duas=Array.isArray(window.ZIKRA_DUAS_AR)?window.ZIKRA_DUAS_AR:[];
if(!track||!duas.length)return;
function makeGroup(){
  const group=document.createElement('div');group.className='dua-group';
  duas.forEach((text,index)=>{
    if(index){const separator=document.createElement('span');separator.className='dua-separator';separator.textContent='۞';group.append(separator);}
    const item=document.createElement('span');item.className='dua-item';item.textContent=text;group.append(item);
  });
  return group;
}
track.replaceChildren(makeGroup(),makeGroup());
requestAnimationFrame(()=>{
  const width=track.firstElementChild?.getBoundingClientRect().width||0;
  track.style.setProperty('--dua-duration',Math.max(180,Math.round(width/90))+'s');
});
})();