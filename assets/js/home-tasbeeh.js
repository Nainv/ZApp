(() => {
  'use strict';
  const key='zikra_tasbeeh',$=id=>document.getElementById(id),button=$('homeTasbeehCount'),reset=$('homeTasbeehReset');if(!button||!reset)return;
  const value=$('homeTasbeehValue'),phrase=$('homeTasbeehPhrase'),progress=$('homeTasbeehProgress'),phrases=['سبحان الله','الحمد لله','الله أكبر'];
  let lastPhrase='',fitFrame=0,flashAnimation=null;const card=button.closest('.home-tasbeeh'),flash=document.createElement('span');flash.className='tasbeeh-count-flash';flash.setAttribute('aria-hidden','true');button.prepend(flash);
  let count=Math.max(0,Math.trunc(Number(localStorage.getItem(key))||0)),timer=0,formatter,width=54,lastDigits=0;
  function flush(){clearTimeout(timer);timer=0;try{localStorage.setItem(key,String(count))}catch(_){}}
  function render(){const text=formatter.format(count);value.textContent=text;const nextPhrase=ZikraI18n.t(phrases[Math.floor(count/33)%3]);if(nextPhrase!==lastPhrase){lastPhrase=nextPhrase;phrase.textContent=nextPhrase;scheduleFit()}progress.textContent=formatter.format(count%33)+' / '+formatter.format(33);button.style.setProperty('--tasbeeh-progress',(count%33)/33*360+'deg');if(text.length!==lastDigits){lastDigits=text.length;button.style.fontSize=Math.min(width<70?24:29,(width-16)/text.length*1.4)+'px'}button.setAttribute('aria-label',phrase.textContent+'، '+text)}
  function locale(){const lang=document.documentElement.lang||'ar';formatter=new Intl.NumberFormat('de-DE',{useGrouping:false});$('homeTasbeehTitle').textContent=lang==='ar'?'المسبحة':lang==='de'?'Tasbih-Zähler':'Tasbih';$('homeTasbeehResetLabel').textContent=lang==='ar'?'تصفير':lang==='de'?'Zurücksetzen':'Reset';lastDigits=0;render();scheduleFit()}
  function change(next){count=next;render();clearTimeout(timer);timer=setTimeout(flush,180)}
  button.addEventListener('click',()=>{change(Math.min(Number.MAX_SAFE_INTEGER,count+1));flashAnimation?.cancel();flashAnimation=flash.animate([{opacity:.95,transform:'scale(.88)'},{opacity:0,transform:'scale(1.18)'}],{duration:220,easing:'ease-out'})});button.addEventListener('blur',()=>flashAnimation?.cancel());reset.addEventListener('click',()=>change(0));
  window.addEventListener('pagehide',flush);document.addEventListener('visibilitychange',()=>{if(document.hidden)flush()});
  window.addEventListener('storage',event=>{if(event.key===key){count=Math.max(0,Math.trunc(Number(event.newValue)||0));render()}});
  new MutationObserver(locale).observe(document.documentElement,{attributes:true,attributeFilter:['lang']});
  function fitText(node,max,min,height=false){node.style.fontSize=max+'px';for(let size=max;size>min&&(node.scrollWidth>node.clientWidth+1||(height&&node.scrollHeight>node.clientHeight+1));){size=Math.max(min,size-1);node.style.fontSize=size+'px'}}
  function scheduleFit(){cancelAnimationFrame(fitFrame);fitFrame=requestAnimationFrame(()=>{if(!card.clientWidth||!card.clientHeight)return;const narrow=card.clientWidth<220,compact=card.clientHeight<160;fitText($('homeTasbeehTitle'),narrow?15:compact?17:21,10);fitText(card.querySelector('.tasbeeh-tagline'),narrow?9:11,8,true);fitText(phrase,narrow?16:compact?18:21,10,true);fitText($('homeTasbeehResetLabel'),narrow?12:compact?15:17,10,true)})}
  const resize=()=>{width=button.clientWidth||54;lastDigits=0;render();scheduleFit()};document.fonts?.ready.then(scheduleFit);locale();window.addEventListener('resize',resize,{passive:true});resize();
})();
