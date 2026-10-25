/* Cache a physical-direction focus graph. Key presses never scan layout. */
(() => {
  'use strict';
  const selector='button:not(:disabled),a[href],input:not(:disabled),select:not(:disabled),textarea:not(:disabled),[tabindex]:not([tabindex="-1"])';
  let cache=null,dirty=true;
  const invalidate=()=>{dirty=true};
  function scope(){
    const menus=[...document.querySelectorAll('#quranReaderDialog:not([hidden]),#reciterPickerMenu:not([hidden]),#languageMenu:not([hidden]),.choice-overlay:not([hidden]),.confirm-overlay:not([hidden])')];
    if(menus.length)return menus[menus.length-1];
    const modals=[...document.querySelectorAll('.modal-backdrop.open')];
    return modals[modals.length-1]||document.querySelector('.app')||document.body;
  }
  function layoutPoint(element){
    let x=0,y=0,node=element;
    while(node){x+=node.offsetLeft;y+=node.offsetTop;node=node.offsetParent}
    return {element,x:x+element.offsetWidth/2,y:y+element.offsetHeight/2,w:element.offsetWidth,h:element.offsetHeight};
  }
  function rebuild(root){
    const list=[...root.querySelectorAll(selector)].filter(e=>!e.closest('[hidden],[aria-hidden="true"]')&&e.offsetWidth&&e.offsetHeight);
    const points=list.map(layoutPoint),edges=new Map();
    if(points.length>60){
      const rows=[];for(const point of points.slice().sort((a,b)=>a.y-b.y||a.x-b.x)){let row=rows[rows.length-1];if(!row||Math.abs(row[0].y-point.y)>8){row=[];rows.push(row)}row.push(point)}
      rows.forEach(row=>row.sort((a,b)=>a.x-b.x));rows.forEach((row,i)=>row.forEach((point,j)=>{const nearest=items=>items?.reduce((best,p)=>!best||Math.abs(p.x-point.x)<Math.abs(best.x-point.x)?p:best,null)?.element;edges.set(point.element,{left:row[j-1]?.element,right:row[j+1]?.element,up:nearest(rows[i-1]),down:nearest(rows[i+1])})}));
    }else for(const from of points){
      const links={};
      for(const [direction,dx,dy] of [['left',-1,0],['right',1,0],['up',0,-1],['down',0,1]]){
        const candidates=[];
        for(const to of points){
          if(to===from)continue;
          const primary=dx?(to.x-from.x)*dx:(to.y-from.y)*dy;
          if(primary<2)continue;
          const cross=dx?Math.abs(to.y-from.y):Math.abs(to.x-from.x);
          const overlap=dx?(from.h+to.h)/2-cross:(from.w+to.w)/2-cross;
          candidates.push({to,primary,cross,aligned:overlap>2,score:primary+cross*2.5});
        }
        const aligned=candidates.filter(c=>c.aligned),pool=aligned.length?aligned:candidates.filter(c=>c.cross<=c.primary);
        pool.sort((a,b)=>aligned.length?a.primary-b.primary||a.cross-b.cross:a.score-b.score);
        if(pool[0])links[direction]=pool[0].to.element;
      }
      edges.set(from.element,links);
    }
    if(root.classList.contains('app')){
      const rtl=document.documentElement.dir!=='ltr';const byId=id=>root.querySelector('#'+id),link=(id,values)=>{const node=byId(id);if(!edges.has(node))return;for(const [direction,target] of Object.entries(values)){const axis=!rtl&&['left','right'].includes(direction)&&!id.startsWith('mini')?(direction==='left'?'right':'left'):direction;edges.get(node)[axis]=target?byId(target):null}};
      // Explicit lanes for nested controls keep the large weather/memory tiles from stealing focus.
      link('homeRefreshBtn',{left:null,right:'settingsBtn',down:'datePanel'});
      link('settingsBtn',{left:'homeRefreshBtn',right:byId('updateNotice')&&!byId('updateNotice').hidden?'updateNotice':null,down:'datePanel'});link('updateNotice',{left:'settingsBtn',right:null,down:'datePanel'});
      link('homeTasbeehCount',{left:null,right:'homeTasbeehReset',up:'settingsBtn',down:'box-Fajr'});
      link('homeTasbeehReset',{left:'homeTasbeehCount',right:'datePanel',up:'settingsBtn',down:'box-Fajr'});
      link('datePanel',{left:'homeTasbeehCount',right:null,up:'settingsBtn',down:'box-Fajr'});
      link('memoryCard',{right:'openQuranFromPlayer',left:null,up:'box-Maghrib',down:null});
      link('openQuranFromPlayer',{left:'memoryCard',right:null,up:'box-Fajr',down:'miniPlay'});
      link('miniPrev',{right:'miniPlay',left:rtl?'memoryCard':null,up:'openQuranFromPlayer',down:null});
      link('miniPlay',{right:'miniNext',left:'miniPrev',up:'openQuranFromPlayer',down:null});
      link('miniNext',{left:'miniPlay',right:rtl?null:'memoryCard',up:'openQuranFromPlayer',down:null});
      const prayers=points.filter(p=>p.element.matches('.prayer-strip .prayer')),rows=[];
      for(const p of prayers){let row=rows.find(row=>Math.abs(row[0].y-p.y)<8);if(!row){row=[];rows.push(row)}row.push(p)}rows.sort((a,b)=>a[0].y-b[0].y);rows.forEach(row=>row.sort((a,b)=>a.x-b.x));
      rows.forEach((row,i)=>row.forEach((p,j)=>{const links=edges.get(p.element),nearest=items=>items.slice().sort((a,b)=>Math.abs(a.x-p.x)-Math.abs(b.x-p.x))[0].element;links.left=row[j-1]?.element;links.right=row[j+1]?.element;if(rows[i-1])links.up=nearest(rows[i-1]);else links.up=byId((rtl?p.x<root.offsetWidth*.3:p.x>root.offsetWidth*.7)?'homeTasbeehCount':'datePanel');if(rows[i+1])links.down=nearest(rows[i+1]);else links.down=byId(p.x<(root.offsetWidth*.5)?'memoryCard':'openQuranFromPlayer')}));
      const circle=['Fajr','Dhuhr','Asr','Maghrib','Isha'].map(key=>byId('box-'+key)).filter(node=>edges.has(node));circle.forEach((node,i)=>{edges.get(node)[rtl?'left':'right']=circle[(i+1)%circle.length];edges.get(node)[rtl?'right':'left']=circle[(i-1+circle.length)%circle.length]});
    }
    cache={root,list,edges};dirty=false;
  }
  function focus(target){
    if(!target)return;
    target.focus({preventScroll:true});
    // Only scroll on small screens or in a scrolling list; never smooth-scroll on a key press.
    if(innerWidth<=900||target.closest('.surah-grid,.reader-picker-menu,.choice-options,.editor-body,#verses'))target.scrollIntoView({block:'nearest',inline:'nearest',behavior:'instant'});
  }
  function move(direction){
    if(!['left','right','up','down'].includes(direction))return false;
    const current=document.activeElement;
    if(current?.matches('input:not([readonly]),textarea,[contenteditable=true]')&&['left','right'].includes(direction))return false;
    const root=scope();if(dirty||cache?.root!==root)rebuild(root);
    if(!cache.list.length)return true;
    if(!cache.edges.has(current)){focus(root.querySelector('#closeQuran')||cache.list[0]);return true}
    focus(cache.edges.get(current)[direction]);return true;
  }
  window.ZikraNavigation={move,invalidate,list(){const root=scope();if(dirty||cache?.root!==root)rebuild(root);return cache.list}};
  window.addEventListener('resize',invalidate,{passive:true});
  const start=()=>{
    new MutationObserver(records=>{
      if(records.some(r=>r.type==='attributes'?r.oldValue!==r.target.getAttribute(r.attributeName)&&(r.target.matches('button,a,input,select,textarea,[tabindex]')||r.target.querySelector(selector)):[...r.addedNodes,...r.removedNodes].some(n=>n.nodeType===1&&(n.matches?.(selector)||n.querySelector?.(selector)))))invalidate();
    }).observe(document.body,{subtree:true,childList:true,attributes:true,attributeOldValue:true,attributeFilter:['hidden','disabled','aria-hidden']});
    // Class changes only matter when a dialog opens, not when a timer updates its highlights.
    document.querySelectorAll('.modal-backdrop,#versesView,#surahIndexGrid').forEach(modal=>new MutationObserver(invalidate).observe(modal,{attributes:true,attributeFilter:['class','style']}));
    document.fonts?.ready.then(invalidate);
  };
  if(document.body)start();else document.addEventListener('DOMContentLoaded',start,{once:true});
})();
