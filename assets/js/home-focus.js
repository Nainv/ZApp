(() => {
  'use strict';
  const app=document.querySelector('.app');document.body.classList.add('remote-focus-active');
  const clear=()=>document.querySelectorAll('.remote-selected').forEach(node=>node.classList.remove('remote-selected'));
  window.addEventListener('blur',()=>{document.body.classList.remove('remote-focus-active');clear()});
  window.addEventListener('focus',()=>document.body.classList.add('remote-focus-active'));
  document.addEventListener('focusin',event=>{if(!event.target.closest('.prayer-strip'))clear()});
  document.addEventListener('keydown',event=>{if(event.key!=='Tab')return;const dialogs=[...document.querySelectorAll('.modal-backdrop.open')],scope=dialogs[dialogs.length-1];if(!scope)return;const controls=getVisibleFocusable(scope),first=controls[0],last=controls[controls.length-1];if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus({preventScroll:true})}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus({preventScroll:true})}},true);
  window.zikraApplySavedSettings=()=>{
    const nextLanguage=localStorage.getItem('zikra_language')||lang,nextCity={name:localStorage.getItem('zikra_city')||city.name,latitude:Number(localStorage.getItem('zikra_lat')??city.latitude),longitude:Number(localStorage.getItem('zikra_lon')??city.longitude)};
    city=nextCity;updateCityLabel();adhanSettings=loadAdhanSettings();if(nextLanguage!==lang)changeLanguage(nextLanguage);else {loadPrayerTimes();loadWeather()}
  };
  window.zikraPrayerSnapshot=()=>({zone:prayerTimeZone,date:prayerDataDate,timings:prayerTimesObj,schedule:prayerSchedule});
})();
