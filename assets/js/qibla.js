/* One location bearing and absolute sensor heading drive the Kaaba, line and arrow. */
(() => {
 'use strict';const wrap=n=>((n%360)+360)%360,delta=(from,to)=>wrap(to-from+180)-180;
 function bearing(lat,lon){if(!Number.isFinite(lat)||!Number.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180||Math.abs(lat-21.4225)+Math.abs(lon-39.8262)<1e-9)return null;const r=Math.PI/180,a=lat*r,d=(39.8262-lon)*r,target=21.4225*r;return wrap(Math.atan2(Math.sin(d),Math.cos(a)*Math.tan(target)-Math.sin(a)*Math.cos(d))*180/Math.PI)}
 function heading(event,screenAngle=0){if(Number.isFinite(event.webkitCompassHeading)&&(event.webkitCompassAccuracy===undefined||event.webkitCompassAccuracy>=0))return wrap(event.webkitCompassHeading+screenAngle);if((event.absolute===true||event.type==='deviceorientationabsolute')&&Number.isFinite(event.alpha))return wrap(360-event.alpha+screenAngle);return null}
 function mount(body,{t,button,locate,saved}){
  const p=(text,cls='help')=>{const n=document.createElement('p');n.className=cls;n.textContent=t(text);body.append(n);return n;};
  p('ضع الهاتف أفقياً وحرّكه ببطء باتجاه الكعبة.');const compass=document.createElement('div');compass.className='compass';compass.id='qiblaCompass';compass.setAttribute('role','img');compass.setAttribute('aria-label',t('بوصلة القبلة والكعبة'));
  compass.innerHTML='<div class="compass-dial"><span class="compass-north">N</span><span class="compass-east">E</span><span class="compass-south">S</span><span class="compass-west">W</span></div><div class="compass-target"><span class="compass-line"></span><span class="compass-arrow"></span><img class="compass-kaaba" src="../../assets/images/kaaba.svg" alt=""></div><span class="compass-center"></span>';
  const ticks=document.createElementNS('http://www.w3.org/2000/svg','svg');ticks.setAttribute('viewBox','0 0 200 200');ticks.setAttribute('class','compass-ticks');ticks.setAttribute('aria-hidden','true');for(let i=0;i<60;i++){const mark=document.createElementNS('http://www.w3.org/2000/svg','line');mark.setAttribute('x1','100');mark.setAttribute('y1','6');mark.setAttribute('x2','100');mark.setAttribute('y2',i%5===0?'19':'13');mark.setAttribute('transform','rotate('+i*6+' 100 100)');mark.setAttribute('class',i%5===0?'major':'minor');ticks.append(mark);}compass.querySelector('.compass-dial').prepend(ticks);
  body.append(compass);const readout=p('','qibla-bearing');readout.id='qiblaBearing';const status=p('اضغط لتحديد الاتجاه.','status');status.id='qiblaStatus';const rotor=compass.querySelector('.compass-target'),dial=compass.querySelector('.compass-dial');
  let alive=true,token=0,target=null,lastHeading=null,lastEvent=null,angle=0,dialAngle=0,aligned=false,frame=null,timer=null;
  const screenAngle=()=>Number(screen.orientation?.angle??window.orientation??0)||0;
  const write=(node,text)=>{const value=t(text);if(node.textContent!==value)node.textContent=value};
  function render(north){if(target===null)return;const wanted=wrap(target-(north??0));angle+=delta(wrap(angle),wanted);dialAngle+=delta(wrap(dialAngle),wrap(-(north??0)));rotor.style.transform='rotate('+angle+'deg)';rotor.style.setProperty('--kaaba-upright',-angle+'deg');dial.style.transform='rotate('+dialAngle+'deg)';compass.dataset.direction=String(wanted);aligned=north!==null&&Math.abs(delta(0,wanted))<=(aligned?7:4);compass.classList.toggle('is-aligned',aligned);write(readout,'القبلة: '+Math.round(target)+'° من الشمال');if(north!==null)write(status,aligned?'أنت باتجاه القبلة.':'اتبع الخط والسهم نحو الكعبة.');}
  function orient(event){if(!alive||target===null)return;lastEvent=event;const value=heading(event,screenAngle());if(value===null)return;if(lastHeading!==null&&Math.abs(delta(lastHeading,value))<.5)return;lastHeading=value;if(frame===null)frame=requestAnimationFrame(()=>{frame=null;if(alive)render(lastHeading)})}
  const rotate=()=>{if(lastEvent)orient(lastEvent)};
  function stop(){window.removeEventListener('deviceorientationabsolute',orient);window.removeEventListener('deviceorientation',orient);window.removeEventListener('orientationchange',rotate);clearTimeout(timer);if(frame!==null)cancelAnimationFrame(frame);frame=null;}
  if(saved){target=bearing(saved.lat,saved.lon);if(target!==null){render(null);write(status,'حسب الموقع المحفوظ؛ اضغط لتحديد موقع الهاتف.');}}
  const trigger=button('تحديد اتجاه القبلة',async()=>{const current=++token;stop();lastHeading=null;lastEvent=null;render(null);write(status,'جارٍ تحديد الموقع…');trigger.disabled=true;try{
   if(typeof DeviceOrientationEvent!=='undefined'&&typeof DeviceOrientationEvent.requestPermission==='function'){const permission=await DeviceOrientationEvent.requestPermission();if(permission!=='granted')throw Error('اسمح بحساس الاتجاه لتتحرك البوصلة.');}
   const coords=await locate();if(!alive||current!==token)return;target=bearing(coords.lat,coords.lon);if(target===null)throw Error('تعذّر حساب اتجاه القبلة لهذا الموقع.');render(null);write(status,'بانتظار حساس الاتجاه؛ الدرجة محسوبة من الشمال.');
   window.addEventListener('deviceorientationabsolute',orient,{passive:true});window.addEventListener('deviceorientation',orient,{passive:true});window.addEventListener('orientationchange',rotate,{passive:true});
   timer=setTimeout(()=>{if(alive&&lastHeading===null)write(status,'حساس الاتجاه غير متاح؛ الدرجة المعروضة محسوبة من الشمال.');},8000);
  }catch(error){if(alive&&current===token)write(status,error.message);}finally{if(alive&&current===token)trigger.disabled=false;}},'secondary');trigger.id='startQibla';body.append(trigger);
  return ()=>{alive=false;token++;stop();};
 }
 const api={bearing,heading,wrap,delta,mount};if(typeof module!=='undefined'&&module.exports)module.exports=api;if(typeof window!=='undefined')window.ZikraQibla=api;
})();
