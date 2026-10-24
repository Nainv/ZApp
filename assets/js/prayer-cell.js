(() => {
  'use strict';const $=id=>document.getElementById(id),prayers={Fajr:'الفجر',Dhuhr:'الظهر',Asr:'العصر',Maghrib:'المغرب',Isha:'العشاء',Sunrise:'الشروق'},icons={Fajr:'☾',Dhuhr:'☀',Asr:'◒',Maghrib:'◉',Isha:'☾',Sunrise:'☀'},cards=[];
  Object.entries(prayers).forEach(([key,title])=>{const button=document.createElement('button');button.className='prayer';button.dataset.prayer=key;button.innerHTML='<span class="prayer-icon" aria-hidden="true">'+icons[key]+'</span><span class="prayer-name">'+title+'</span><strong class="prayer-time" id="time-'+key+'">--:--</strong><span class="next-label">القادمة</span><span class="prayer-countdown" id="cd-'+key+'"></span>';$('prayerStrip').append(button);cards.push(button)});
  let snapshot=null,active=parent===window,timer=null,marker='',initial=true;
  function render(){if(!active||!snapshot)return;const now=Date.now(),model=ZikraPrayer.display(snapshot,now),next=model.next;
    if(marker!==snapshot.date+'|'+next?.key+'|'+next?.date){marker=snapshot.date+'|'+next?.key+'|'+next?.date;cards.forEach(card=>{const key=card.dataset.prayer;$('time-'+key).textContent=(model.timings[key]||'--:--').split(' ')[0];$('cd-'+key).textContent='';card.classList.toggle('next-prayer',next?.key===key);card.classList.toggle('next-day',next?.key===key&&!!model.nextDay)});$('todayDate').textContent=new Intl.DateTimeFormat('ar',{timeZone:snapshot.zone,dateStyle:'full'}).format(new Date());$('status').textContent=snapshot.error||snapshot.source+(snapshot.selectedMosque?' · '+snapshot.selectedMosque:'')}
    if(next){$('cd-'+next.key).textContent=ZikraPrayer.remaining(next.epoch-now);$('nextPrayerName').textContent=prayers[next.key]+(model.nextDay?' · غدًا':'');$('mainCountdown').textContent=ZikraPrayer.remaining(next.epoch-now)}else{$('nextPrayerName').textContent='—';$('mainCountdown').textContent='--:--:--'}
    if(initial&&snapshot.schedule?.length){initial=false;(cards.find(c=>c.dataset.prayer===next?.key)||cards[0]).focus({preventScroll:true})}
  }
  function setActive(value){active=value;clearInterval(timer);timer=null;if(active){render();timer=setInterval(render,1000)}}
  window.zikraRemoteDelegate={ok(){return document.activeElement!==$('cellHomeButton')}};
  window.addEventListener('message',event=>{if(event.source!==parent)return;const m=event.data;if(m?.type==='zikra:context'||m?.type==='zikra:session-start'){if(m.prayer){snapshot=m.prayer;marker='';render()}if(m.type==='zikra:session-start')setActive(true)}else if(m?.type==='zikra:cell-active')setActive(m.active)});
  if(parent!==window)parent.postMessage({type:'zikra:context-request'},'*');else{ZikraPrayer.load(ZikraLocation.read()).then(s=>{snapshot=s;setActive(true)}).catch(error=>{$('status').textContent=error.message})}
})();
