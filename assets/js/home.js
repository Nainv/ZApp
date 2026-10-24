(()=>{
 const buttons=[...document.querySelectorAll('[data-cell]')];
 function openCell(id){
   const known=['settings','weather','prayer','quran','zikra','dua-bar'];
   if(!known.includes(id))return;
   try{localStorage.setItem('zikra_last_cell',id)}catch(e){}
   // Each cell is stored in its own folder in this repository.
   location.href='cells/'+id+'/index.html';
 }
 buttons.forEach((b,i)=>{
   b.addEventListener('click',()=>openCell(b.dataset.cell));
   b.addEventListener('keydown',e=>{
     const key=e.key;
     if(key==='Enter'||key===' '){e.preventDefault();openCell(b.dataset.cell);return;}
     if(!['ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].includes(key))return;
     e.preventDefault();
     const current=b.getBoundingClientRect(),cx=current.left+current.width/2,cy=current.top+current.height/2;
     const candidates=buttons.filter(x=>x!==b).map(x=>{const r=x.getBoundingClientRect();return {el:x,x:r.left+r.width/2,y:r.top+r.height/2}}).filter(o=>key==='ArrowLeft'?o.x<cx:key==='ArrowRight'?o.x>cx:key==='ArrowUp'?o.y<cy:o.y>cy);
     candidates.sort((a,c)=>Math.hypot(a.x-cx,a.y-cy)-Math.hypot(c.x-cx,c.y-cy));
     if(candidates[0])candidates[0].el.focus();
   });
 });
 document.addEventListener('keydown',e=>{if(e.key==='Escape'||e.key==='Backspace'){e.preventDefault();}});
 const last=(()=>{try{return localStorage.getItem('zikra_last_cell')}catch(e){return null}})();
 const first=buttons.find(b=>b.dataset.cell===last)||document.querySelector('.quran');
 window.addEventListener('load',()=>first&&first.focus());
})();
