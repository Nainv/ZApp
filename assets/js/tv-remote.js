/* One input owner per document. Native keys, keyboard keys and forwarded keys share this route. */
(() => {
  'use strict';
  const inputRoot=document.documentElement,setInput=mode=>{if(inputRoot.dataset.inputMode!==mode)inputRoot.dataset.inputMode=mode};setInput(inputRoot.classList.contains('tv-device')?'remote':window.matchMedia?.('(pointer:coarse)').matches?'touch':'remote');window.zikraUseRemoteFocus=()=>setInput('remote');const viewport=()=>{const size=window.visualViewport?.height||window.innerHeight;if(Number.isFinite(size)&&size>0)inputRoot.style.setProperty('--ui-height',Math.round(size)+'px')};viewport();window.addEventListener('resize',viewport,{passive:true});window.visualViewport?.addEventListener('resize',viewport,{passive:true});window.addEventListener('pointerdown',event=>setInput(event.pointerType==='touch'?'touch':'remote'),true);window.addEventListener('touchstart',()=>setInput('touch'),{capture:true,passive:true});window.addEventListener('keydown',event=>{if(['ArrowLeft','ArrowRight','ArrowUp','ArrowDown','Enter','Tab','Escape','Select','OK'].includes(event.key))setInput('remote')},true);

  const directions={ArrowLeft:'left',ArrowRight:'right',ArrowUp:'up',ArrowDown:'down'};
  const delegate=()=>window.zikraRemoteDelegate||{};
  let cellAdhan=false,stoppedClick=false;
  const heldKeys=new Set();
  function stopAdhan(){if(!window.zikraAdhanActive&&!cellAdhan)return false;cellAdhan=false;if(window.zikraStopAdhan)window.zikraStopAdhan();else parent.postMessage({type:'zikra:adhan-stop'},'*');return true}
  const consume=event=>{event.preventDefault();event.stopImmediatePropagation()};
  window.addEventListener('pointerdown',event=>{stoppedClick=stopAdhan();if(stoppedClick)consume(event)},true);
  window.addEventListener('click',event=>{if(stoppedClick||stopAdhan()){stoppedClick=false;consume(event)}},true);
  window.addEventListener('beforeinput',event=>{if(stopAdhan())consume(event)},true);
  window.addEventListener('keyup',event=>{if(heldKeys.delete(event.code||event.key))consume(event)},true);
  function activeFrame(){const overlay=document.getElementById('zikraCellOverlay');return overlay&&!overlay.hidden?document.getElementById('zikraCellFrame'):null}
  function forward(type,extra={}){const frame=activeFrame();if(!frame)return false;frame.contentWindow?.postMessage({type,...extra},'*');return true}
  function move(direction){
    window.zikraUseRemoteFocus?.();
    if(stopAdhan())return true;
    if(forward('zikra:cell-dpad',{direction}))return true;
    if(delegate().move?.(direction))return true;
    const current=document.activeElement;
    if(current?.remoteMove?.(direction))return true;
    if(current?.matches('input:not([readonly]),textarea,[contenteditable=true]')&&['left','right'].includes(direction))return false;
    return window.ZikraNavigation?.move(direction)!==false;
  }
  function ok(){window.zikraUseRemoteFocus?.();if(stopAdhan())return;if(forward('zikra:cell-ok'))return;if(delegate().ok?.())return;const current=document.activeElement;if(current?.remoteOK){current.remoteOK();return}if(current?.matches('button,[role=button],a[href]'))current.click()}
  function back(){window.zikraUseRemoteFocus?.();if(stopAdhan())return;if(window.zikraBackFromRemote){window.zikraBackFromRemote();return}if(delegate().back){delegate().back();return}close()}
  function close(){if(parent!==window){parent.postMessage({type:'zikra:cell-close'},'*');return}location.href='../../index.html'}
  function first(){const list=window.ZikraNavigation?.list()||[];if(list.length&&!list.includes(document.activeElement)){const brand=document.getElementById('brandDuaBtn');(list.includes(brand)?brand:list[0]).focus({preventScroll:true})}}
  document.addEventListener('keydown',event=>{
    if(event.defaultPrevented)return;
    if(heldKeys.has(event.code||event.key)){consume(event);return}
    if(!['AudioVolumeUp','AudioVolumeDown','AudioVolumeMute','VolumeUp','VolumeDown','VolumeMute'].includes(event.key)&&![173,174,175].includes(event.keyCode)&&stopAdhan()){heldKeys.add(event.code||event.key);consume(event);return}
    const direction=directions[event.key]||({37:'left',38:'up',39:'right',40:'down'})[event.keyCode];
    if(direction){if(move(direction)){event.preventDefault();event.stopImmediatePropagation()}return}
    if(['Enter','Select','OK',' '].includes(event.key)){
      if(event.key===' '&&document.activeElement?.matches('input:not([readonly]),textarea'))return;
      if(document.activeElement?.matches('textarea,input:not([readonly])')&&document.activeElement.id!=='manualCity')return;
      event.preventDefault();event.stopImmediatePropagation();ok();return;
    }
    if(['Escape','Backspace','BrowserBack','GoBack','XF86Back'].includes(event.key)||event.keyCode===4){
      if(event.key==='Backspace'&&document.activeElement?.matches('input:not([readonly]),textarea'))return;
      event.preventDefault();event.stopImmediatePropagation();back();return;
    }
    if(event.key==='Tab'){
      if(forward('zikra:cell-tab',{reverse:event.shiftKey})){event.preventDefault();event.stopImmediatePropagation();return}
      const list=window.ZikraNavigation?.list()||[];if(!list.length)return;const i=list.indexOf(document.activeElement),next=(i+(event.shiftKey?-1:1)+list.length)%list.length;
      event.preventDefault();list[next].focus({preventScroll:true});
    }
    const commands={MediaPlay:'play',MediaPause:'pause',MediaPlayPause:'toggle',MediaStop:'stop',MediaTrackNext:'next',MediaTrackPrevious:'previous'};
    if(commands[event.key]){event.preventDefault();window.handleAndroidMediaKey?.(commands[event.key])}
  },true);
  window.addEventListener('message',event=>{
    if(event.source!==parent)return;const m=event.data;
    if(m?.type==='zikra:context'||m?.type==='zikra:session-start')cellAdhan=!!m.adhanActive;else if(m?.type==='zikra:cell-dpad')move(m.direction);else if(m?.type==='zikra:cell-ok')ok();else if(m?.type==='zikra:cell-back'||m?.type==='zikra:settings-back')back();else if(m?.type==='zikra:cell-focus')first();else if(m?.type==='zikra:cell-tab'){if(stopAdhan())return;const list=window.ZikraNavigation?.list()||[],i=list.indexOf(document.activeElement);list[(i+(m.reverse?-1:1)+list.length)%list.length]?.focus({preventScroll:true})}
  });
  window.closeZikraCell=close;window.handleAndroidDpad=direction=>move(String(direction).toLowerCase());window.handleAndroidOk=ok;window.handleAndroidBack=back;
  const closeButton=document.getElementById('cellHomeButton');if(closeButton)closeButton.addEventListener('click',()=>delegate().back?delegate().back():close());
  if(document.readyState==='complete')first();else window.addEventListener('load',first,{once:true});
})();
