(() => {
  'use strict';
  const $=id=>document.getElementById(id),hub=$('settingsGrid'),editor=$('editor'),body=$('editorBody'),picker=$('choiceOverlay'),confirm=$('confirmOverlay');
  const prayers={Fajr:'الفجر',Dhuhr:'الظهر',Asr:'العصر',Maghrib:'المغرب',Isha:'العشاء'},voices={makkah:'أذان مكة',madinah:'أذان المدينة',alafasy:'مشاري العفاسي',syria:'أذان سوري',silent:'بلا أذان'},languages={ar:'العربية',de:'Deutsch',en:'English'},methods={3:'رابطة العالم الإسلامي',4:'أم القرى ـ مكة',2:'أمريكا الشمالية',5:'الهيئة المصرية',1:'جامعة كراتشي',13:'الشؤون الدينية ـ تركيا'};
  let adhan={};try{adhan=JSON.parse(localStorage.getItem('zikra_adhan_settings_v1')||'{}')}catch(_){}
  const defaults=p=>({...ZikraLocation.normalize(p),language:['ar','de','en'].includes(p?.language)?p.language:'ar',prayers:Object.fromEntries(Object.keys(prayers).map(key=>[key,{enabled:p?.prayers?.[key]?.enabled!==false,voice:voices[p?.prayers?.[key]?.voice]?p.prayers[key].voice:'makkah'}]))});
  let draft=defaults({...ZikraLocation.read(),language:localStorage.getItem('zikra_language')||'ar',prayers:adhan}),saved=JSON.stringify(draft),view='',returnTile=null,choiceReturn=null,choiceMove=null,cleanup=()=>{},toastTimer,pending=null,sequence=0;
  const dirty=()=>JSON.stringify(draft)!==saved;
  const toast=text=>{$('toast').textContent=text;$('toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').hidden=true,3000)};
  function summary(){$('languageSummary').textContent=languages[draft.language];$('locationSummary').textContent=ZikraLocation.valid(draft.lat,draft.lon)?draft.city:'اختر موقعك';$('methodSummary').textContent=draft.mosque?.title||'بحسب الموقع';$('saveNote').textContent=dirty()?'تغييرات بانتظار الحفظ':'كل الإعدادات محفوظة'}
  function button(text,action,cls='choice-trigger'){const node=document.createElement('button');node.type='button';node.className=cls;node.textContent=text;node.addEventListener('click',action);return node}
  function paragraph(text,cls='help'){const node=document.createElement('p');node.className=cls;node.textContent=text;body.append(node);return node}
  function closePicker(){picker.hidden=true;choiceMove=null;window.AndroidTV?.hideKeyboard?.();if(choiceReturn?.isConnected)choiceReturn.focus({preventScroll:true})}
  function choices(title,options,current,onSelect){
    choiceReturn=document.activeElement;const list=$('choiceOptions');list.replaceChildren();$('choiceTitle').textContent=title;
    options.forEach(([value,text])=>{const node=button(text,()=>{closePicker();onSelect(value);summary()},'choice-option');node.dataset.value=value;node.setAttribute('aria-selected',String(value===current));list.append(node)});
    picker.hidden=false;choiceMove=direction=>{const nodes=[...list.children],i=nodes.indexOf(document.activeElement);if(direction==='up'||direction==='down'){const next=(i+(direction==='down'?1:-1)+nodes.length)%nodes.length;nodes[next]?.focus({preventScroll:true});nodes[next]?.scrollIntoView({block:'nearest',behavior:'instant'})}return true};
    (list.querySelector('[aria-selected=true]')||list.firstElementChild)?.focus({preventScroll:true});
  }
  function findCity(){
    choiceReturn=document.activeElement;$('choiceTitle').textContent='اختيار المدينة';const list=$('choiceOptions');list.replaceChildren();choiceMove=null;
    const input=document.createElement('input');input.id='manualCity';input.type='text';input.className='field';input.placeholder='اكتب اسم المدينة';input.setAttribute('aria-label','اسم المدينة');input.readOnly=true;input.autocomplete='off';
    const status=document.createElement('p');status.className='help';status.textContent='اضغط OK على الاسم للكتابة، ثم اضغط بحث.';
    const results=document.createElement('div');results.className='city-results';let searching=false;
    const search=async()=>{if(searching)return;searching=true;input.readOnly=true;window.AndroidTV?.hideKeyboard?.();status.textContent='جارٍ البحث…';try{const found=await ZikraMosques.cities(input.value);results.replaceChildren();found.forEach(place=>results.append(button(place.name+' · '+(place.admin1||'')+' · '+(place.country||''),()=>{draft={...draft,city:place.name,lat:place.latitude,lon:place.longitude,zone:place.timezone,mosque:null,locationMode:'city'};closePicker();showView('location');summary()},'choice-option')));status.textContent='اختر المدينة من النتائج.';results.firstElementChild?.focus({preventScroll:true})}catch(error){status.textContent=error.message}finally{searching=false}};
    input.addEventListener('click',()=>{input.readOnly=false;input.focus();window.AndroidTV?.showKeyboard?.()});input.addEventListener('blur',()=>input.readOnly=true);
    input.remoteOK=()=>{if(input.readOnly){input.readOnly=false;window.AndroidTV?.showKeyboard?.()}else search()};
    const searchButton=button('بحث',search,'secondary');searchButton.id='citySearchButton';list.append(input,searchButton,status,results);picker.hidden=false;input.focus({preventScroll:true});
  }
  async function useLocation(){
    const status=$('locationStatus');if(status)status.textContent='جارٍ تحديد موقع الجهاز…';
    try{const coords=await ZikraLocation.locate();draft={...draft,...coords,city:'موقعي الحالي',mosque:null,locationMode:'gps',zone:Intl.DateTimeFormat().resolvedOptions().timeZone};showView('location');summary();toast('تم تحديد الموقع. اختر المسجد أو اضغط حفظ.')}catch(error){if(status)status.textContent=error.message}
  }
  async function findMosques(){
    if(!ZikraLocation.valid(draft.lat,draft.lon)){toast('حدد موقعك أو اختر المدينة أولًا.');return}
    const status=$('locationStatus');if(status)status.textContent='جارٍ البحث حول '+draft.city+'…';
    try{
      const found=await ZikraMosques.nearby(draft.lat,draft.lon);
      if(!found.length){if(status)status.textContent='لا يوجد مسجد مسجّل في الدليل ضمن 25 كم. مواقيت موقعك تبقى ظاهرة.';return}
      choices('المساجد حول '+draft.city,found.map((m,i)=>[String(i),m.title+' · '+m.distance.toFixed(1)+' كم']),String(found.findIndex(m=>m.id===draft.mosque?.id)),value=>{
        const mosque=found[Number(value)];draft={...draft,lat:mosque.lat,lon:mosque.lon,city:mosque.city||draft.city,mosque,locationMode:'mosque'};
        persist(false).then(()=>{showView('location');toast('تم تثبيت '+mosque.title)}).catch(error=>toast(error.message));
      });if(status)status.textContent='المساجد مرتبة من الأقرب إلى الأبعد.';
    }catch(error){if(status)status.textContent=error.message}
  }
  function showView(name){
    cleanup();cleanup=()=>{};view=name;hub.hidden=true;editor.hidden=false;body.replaceChildren();
    $('editorTitle').textContent=({language:'لغة التطبيق',location:'الموقع والمسجد',method:'الموقع والمسجد',adhan:'الأذان لكل صلاة',qibla:'اتجاه القبلة',about:'عن ذِكرى'})[name];
    if(name==='language'){const trigger=button(languages[draft.language],()=>choices('لغة التطبيق',Object.entries(languages),draft.language,value=>{draft.language=value;trigger.textContent=languages[value]}));trigger.id='languagePickerButton';body.append(trigger)}
    if(name==='location'||name==='method'){
      const selected=paragraph(draft.mosque?'المسجد المثبّت: '+draft.mosque.title:ZikraLocation.valid(draft.lat,draft.lon)?'الموقع الحالي: '+draft.city:'الموقع لم يُحدّد بعد','location-current');selected.id='selectedLocation';
      paragraph('تظهر المواقيت حسب موقعك. اختيار المسجد يثبّت موقعه ويحفظه مباشرة.');
      body.append(button('تحديد موقعي الحالي',useLocation,'location-action'),button('اختيار مدينة أو تغييرها',findCity,'location-action'),button(draft.mosque?'تغيير المسجد المثبّت':'اختيار مسجد قريب',findMosques,'location-action'));
      const status=paragraph(ZikraLocation.valid(draft.lat,draft.lon)?'سيتم البحث عن المساجد حول '+draft.city+'.':'حدد موقع الجهاز أو اختر المدينة أولًا.','status');status.id='locationStatus';
      if(draft.mosque)body.append(button('إلغاء تثبيت المسجد واستخدام الموقع',()=>{draft.mosque=null;draft.locationMode='city';persist(false).then(()=>showView('location')).catch(e=>toast(e.message))},'secondary'));
      const method=button('طريقة الحساب: '+(methods[draft.method]||methods[3]),()=>choices('طريقة حساب المواقيت',Object.entries(methods),draft.method,value=>{draft.method=value;method.textContent='طريقة الحساب: '+methods[value]}),'secondary');body.append(method);
      paragraph('المواقيت محسوبة للموقع، وقد تختلف عن جدول خاص يضعه المسجد. بيانات المساجد: PrayerZone.');
    }
    if(name==='adhan'){const list=document.createElement('div');list.className='prayer-options';Object.entries(prayers).forEach(([key,title])=>{const row=button('',()=>choices('أذان '+title,Object.entries(voices),draft.prayers[key].enabled?draft.prayers[key].voice:'silent',value=>{draft.prayers[key]={enabled:value!=='silent',voice:value==='silent'?draft.prayers[key].voice:value};desc.textContent=voices[value]}),'prayer-row');const titleNode=document.createElement('strong'),desc=document.createElement('small');titleNode.textContent=title;desc.textContent=draft.prayers[key].enabled?voices[draft.prayers[key].voice]:voices.silent;row.append(titleNode,desc);list.append(row)});body.append(list)}
    if(name==='about'){paragraph('ذِكرى… طمأنينة ترافق يومك. مساحة تجمع تلاوة القرآن الكريم، ومواقيت الصلاة، والأذكار، لتبقى لحظات الذكر قريبة منك في بيتك وفي كل وقت.','about-copy');paragraph('© ذِكرى — Directed by Nabil Koukach');const link=document.createElement('a');link.href='https://pray.zone/';link.textContent='دليل المساجد: PrayerZone';body.append(link)}
    if(name==='qibla'){
      paragraph('استخدم الهاتف لتحديد اتجاه القبلة.');const compass=document.createElement('div');compass.className='compass';const needle=document.createElement('span');needle.className='compass-needle';needle.textContent='▲';compass.append(needle);body.append(compass);const status=paragraph('اضغط لتحديد الاتجاه.','status');
      body.append(button('تحديد اتجاه القبلة',async()=>{try{if(typeof DeviceOrientationEvent!=='undefined'&&typeof DeviceOrientationEvent.requestPermission==='function')await DeviceOrientationEvent.requestPermission();const coords=await ZikraLocation.locate(),lat=coords.lat*Math.PI/180,delta=(39.8262-coords.lon)*Math.PI/180,target=21.4225*Math.PI/180,bearing=(Math.atan2(Math.sin(delta),Math.cos(lat)*Math.tan(target)-Math.sin(lat)*Math.cos(delta))*180/Math.PI+360)%360;needle.style.transform='rotate('+bearing+'deg)';status.textContent='القبلة: '+Math.round(bearing)+'° من الشمال';const eventName='ondeviceorientationabsolute'in window?'deviceorientationabsolute':'deviceorientation';const heading=e=>{const north=Number.isFinite(e.webkitCompassHeading)?e.webkitCompassHeading:360-(e.alpha||0);needle.style.transform='rotate('+(bearing-north)+'deg)'};window.addEventListener(eventName,heading,{passive:true});cleanup=()=>window.removeEventListener(eventName,heading)}catch(error){status.textContent=error.message}},'secondary'));
    }
    (body.querySelector('button')||$('backEditor')).focus({preventScroll:true});
  }
  function showHub(){cleanup();cleanup=()=>{};editor.hidden=true;hub.hidden=false;view='';returnTile?.focus({preventScroll:true})}
  function goHome(){cleanup();if(parent!==window)parent.postMessage({type:'zikra:cell-close'},'*');else location.href='../../index.html'}
  function closeSettings(){if(!dirty()){goHome();return}confirm.hidden=false;$('confirmSave').focus({preventScroll:true})}
  function persist(close=true){
    if(pending)return pending.promise;
    const payload=defaults(draft);draft=payload;const id=String(++sequence);let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no});
    const timer=setTimeout(()=>{if(pending?.id===id){pending=null;$('saveSettings').disabled=false;reject(Error('تعذّر تأكيد الحفظ. حاول مجددًا.'))}},8000);
    pending={id,promise,payload,close,resolve,reject,timer};$('saveSettings').disabled=true;
    if(parent!==window)parent.postMessage({type:'zikra:settings-save',preferences:payload,requestId:id},'*');
    else {try{ZikraLocation.save(payload);localStorage.setItem('zikra_language',payload.language);localStorage.setItem('zikra_adhan_settings_v1',JSON.stringify(payload.prayers));finishSave(id)}catch(error){failSave(id,error.message)}}
    return promise;
  }
  function finishSave(id){if(!pending||pending.id!==id)return;const p=pending;pending=null;clearTimeout(p.timer);saved=JSON.stringify(p.payload);$('saveSettings').disabled=false;summary();p.resolve();if(p.close)goHome()}
  function failSave(id,message){if(!pending||pending.id!==id)return;const p=pending;pending=null;clearTimeout(p.timer);$('saveSettings').disabled=false;p.reject(Error(message))}
  window.zikraSaveForUpdate=()=>dirty()?persist(false):Promise.resolve();
  function back(){if(!picker.hidden){closePicker();return}if(!confirm.hidden){confirm.hidden=true;return}if(view){showHub();return}closeSettings()}
  window.zikraRemoteDelegate={
    back,
    move(direction){if(!picker.hidden&&choiceMove)return choiceMove(direction);if(document.activeElement.id==='manualCity'&&direction==='down'){document.activeElement.readOnly=true;window.AndroidTV?.hideKeyboard?.();$('citySearchButton').focus();return true}return false},
    ok(){const input=document.activeElement;if(input.id==='manualCity'){input.remoteOK?.();return true}return false}
  };
  document.querySelectorAll('[data-view]').forEach(tile=>tile.addEventListener('click',()=>{returnTile=tile;showView(tile.dataset.view)}));
  $('backEditor').addEventListener('click',showHub);$('closeSettings').addEventListener('click',closeSettings);
  $('saveSettings').addEventListener('click',()=>persist().catch(e=>toast(e.message)));$('confirmSave').addEventListener('click',()=>persist().catch(e=>toast(e.message)));$('confirmDiscard').addEventListener('click',goHome);
  window.addEventListener('message',event=>{
    if(event.source!==parent)return;const m=event.data;
    if(m?.type==='zikra:session-start'||m?.type==='zikra:context'&&!dirty()&&!pending){if(!m.preferences)return;draft=defaults(m.preferences);saved=JSON.stringify(draft);if(m.type==='zikra:session-start'){picker.hidden=true;confirm.hidden=true;showHub();document.querySelector('[data-view=language]').focus({preventScroll:true})}summary()}
    else if(m?.type==='zikra:settings-saved')finishSave(m.requestId);
    else if(m?.type==='zikra:settings-error')failSave(m.requestId,m.message);
  });
  if(parent!==window)parent.postMessage({type:'zikra:context-request'},'*');
  summary();document.querySelector('[data-view=language]').focus({preventScroll:true});
})();
