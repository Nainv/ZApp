/* A single persisted location. Changing it always clears a mosque from the previous area. */
(() => {
  'use strict';
  const number=value=>value===null||value===undefined||value===''?null:Number(value);
  function valid(lat,lon){return Number.isFinite(lat)&&Math.abs(lat)<=90&&Number.isFinite(lon)&&Math.abs(lon)<=180}
  function normalize(p={}){
    let lat=number(p.lat),lon=number(p.lon),mosque=null;
    if(p.mosque&&typeof p.mosque.id==='string'&&typeof p.mosque.title==='string'){
      const mlat=number(p.mosque.lat),mlon=number(p.mosque.lon);
      if(valid(mlat,mlon)){mosque={id:p.mosque.id,title:p.mosque.title,city:p.mosque.city||p.city||'',lat:mlat,lon:mlon};lat=mlat;lon=mlon}
    }
    if(!valid(lat,lon)){lat=null;lon=null;mosque=null}
    let zone=p.zone||Intl.DateTimeFormat().resolvedOptions().timeZone;try{new Intl.DateTimeFormat('en',{timeZone:zone})}catch(_){zone=Intl.DateTimeFormat().resolvedOptions().timeZone}
    return {city:typeof p.city==='string'&&p.city.trim()?p.city.trim():'حدد الموقع',lat,lon,zone,mosque,locationMode:p.locationMode||'city',method:String([1,2,3,4,5,13].includes(Number(p.method))?p.method:3)};
  }
  function read(){let mosque=null;try{mosque=JSON.parse(localStorage.getItem('zikra_mosque')||'null')}catch(_){}return normalize({city:localStorage.getItem('zikra_city'),lat:localStorage.getItem('zikra_lat'),lon:localStorage.getItem('zikra_lon'),zone:localStorage.getItem('zikra_timezone'),method:localStorage.getItem('zikra_prayer_method'),locationMode:localStorage.getItem('zikra_location_mode'),mosque})}
  function save(value){const p=normalize(value);localStorage.setItem('zikra_city',p.city);for(const [key,value] of [['zikra_lat',p.lat],['zikra_lon',p.lon]])value===null?localStorage.removeItem(key):localStorage.setItem(key,String(value));localStorage.setItem('zikra_timezone',p.zone);localStorage.setItem('zikra_location_mode',p.locationMode);localStorage.setItem('zikra_prayer_method',p.method);p.mosque?localStorage.setItem('zikra_mosque',JSON.stringify(p.mosque)):localStorage.removeItem('zikra_mosque');return p}
  const locate=()=>new Promise((resolve,reject)=>{if(!navigator.geolocation){reject(Error('هذا الجهاز لا يوفّر تحديد الموقع. اختر المدينة.'));return}navigator.geolocation.getCurrentPosition(({coords})=>resolve({lat:coords.latitude,lon:coords.longitude}),()=>reject(Error('تعذّر تحديد الموقع. اسمح بالوصول إليه أو اختر المدينة يدويًا.')),{enableHighAccuracy:false,timeout:12000,maximumAge:120000})});
  async function refreshAuthorizedGPS(){const p=read();if(p.mosque||valid(p.lat,p.lon)&&p.locationMode!=='gps')return null;try{let allowed=window.AndroidTV?.hasLocationPermission?.()===true;if(!allowed&&navigator.permissions)allowed=(await navigator.permissions.query({name:'geolocation'})).state==='granted';if(!allowed)return null;const coords=await locate();if(valid(p.lat,p.lon)&&Math.abs(coords.lat-p.lat)<.002&&Math.abs(coords.lon-p.lon)<.002)return null;return save({...p,...coords,city:'موقعي الحالي',locationMode:'gps'})}catch(_){return null}}
  window.ZikraLocation={read,save,normalize,valid,locate,refreshAuthorizedGPS};
})();
