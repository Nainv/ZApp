(() => {
  'use strict';
  const key='zikra_tasbeeh',$=id=>document.getElementById(id),button=$('homeTasbeehCount'),reset=$('homeTasbeehReset');if(!button||!reset)return;
  const value=$('homeTasbeehValue'),phrase=$('homeTasbeehPhrase'),progress=$('homeTasbeehProgress'),phrases=['سبحان الله','الحمد لله','الله أكبر'];
  let count=Math.max(0,Math.trunc(Number(localStorage.getItem(key))||0)),timer=0,formatter,width=54,lastDigits=0;
  function flush(){clearTimeout(timer);timer=0;try{localStorage.setItem(key,String(count))}catch(_){}}
  function render(){const text=formatter.format(count);value.textContent=text;phrase.textContent=ZikraI18n.t(phrases[Math.floor(count/33)%3]);progress.textContent=formatter.format(count%33)+' / '+formatter.format(33);button.style.setProperty('--tasbeeh-progress',(count%33)/33*360+'deg');if(text.length!==lastDigits){lastDigits=text.length;button.style.fontSize=Math.min(width<70?24:29,(width-16)/text.length*1.4)+'px'}button.setAttribute('aria-label',phrase.textContent+'، '+text)}
  function locale(){const lang=document.documentElement.lang||'ar';formatter=new Intl.NumberFormat(lang==='ar'?'ar-u-nu-arab':'en',{useGrouping:false});$('homeTasbeehTitle').textContent=lang==='ar'?'المسبحة':lang==='de'?'Tasbih-Zähler':'Tasbih';$('homeTasbeehResetLabel').textContent=lang==='ar'?'تصفير':lang==='de'?'Zurücksetzen':'Reset';lastDigits=0;render()}
  function change(next){count=next;render();clearTimeout(timer);timer=setTimeout(flush,180)}
  button.addEventListener('click',()=>change(Math.min(Number.MAX_SAFE_INTEGER,count+1)));reset.addEventListener('click',()=>change(0));
  window.addEventListener('pagehide',flush);document.addEventListener('visibilitychange',()=>{if(document.hidden)flush()});
  window.addEventListener('storage',event=>{if(event.key===key){count=Math.max(0,Math.trunc(Number(event.newValue)||0));render()}});
  new MutationObserver(locale).observe(document.documentElement,{attributes:true,attributeFilter:['lang']});
  const resize=()=>{width=button.clientWidth||54;lastDigits=0;render()};locale();window.addEventListener('resize',resize,{passive:true});resize();
})();
