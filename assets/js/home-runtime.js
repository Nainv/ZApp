"use strict";
const $ = (id) => document.getElementById(id);
const appNow = () => window.ZikraClock?.now() ?? Date.now();

const API = "https://mp3quran.net/api/v3";

let lang = localStorage.getItem("zikra_language") || "ar";

if (!["ar", "en", "de"].includes(lang)) lang = "ar";

let weatherError = '',weatherData = null,prayerTimesObj = null,prayerTimeZone = null,city = { name: ZikraLocation.read().city, latitude: ZikraLocation.read().lat, longitude: ZikraLocation.read().lon };

let previousFocus = null,readers = [],times = [],read = null,loaded = false,currentSurahNum = 1,surahListCache = [],lastBookmarkTime = 0;

const audio = $("audio"),versesBox = $("verses"),statusBox = $("quranStatus");

let lastBookmarkIdentity='',bookmarkViewSignature='',renderedAyahSignature='',renderedAyahElement=null;const quranNumberFormats=new Map();
const T = {
  ar: {title: "ذِكرى",subtitle: "طمأنينةٌ للقلب في كل يوم",quranTitle: "فهرس القرآن الكريم",reciterLabel: "اختر القارئ:",noResume: "لا توجد قراءة سابقة",defaultSurah: "اختر سورة وابدأ التلاوة",sourceLabel: "المصدر: ",rights: "© 2026 ذِكرى — جميع الحقوق محفوظة",memories: [{ text: "﴿ فَإِنَّ مَعَ الْعُسْرِ يُسْرًا ﴾", source: "سورة الشرح، الآية 5", benefit: "تذكّرنا الآية الكريمة بأن كل عسر وتعب يتبعه يسر من الله تعالى، وأن الضيق لا يدوم." }, { text: "«إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ»", source: "صحيح البخاري، حديث 1؛ وصحيح مسلم، حديث 1907", benefit: "النية الخالصة هي أساس قبول الأعمال؛ فاصدق مع الله يصدقك ويصلح لك شأنك." }, { text: "﴿ أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ ﴾", source: "سورة الرعد، الآية 28", benefit: "ذكر الله تعالى هو الدواء الأعظم لراحة النفوس وسكينة القلوب وطمأنينتها." }, { text: "«مَنْ لَا يَرْحَمُ لَا يُرْحَمُ»", source: "صحيح البخاري، حديث 6013؛ وصحيح مسلم، حديث 2319", benefit: "الرحمة بالخلق سبب لرحمة الخالق؛ فكن رحيماً لمن حولك يرحمك الله." }, { text: "﴿ وَقُلْ رَبِّ زِدْنِي عِلْمًا ﴾", source: "سورة طه، الآية 114", benefit: "اطلب العلم والتدبر واجعل حياتك زاخرة بالتعلم والعمل الصالح الذي ينفعك." }]},
  en: {title: "Zikra",subtitle: "Peace for the heart, every day",quranTitle: "Quran Index",reciterLabel: "Select Reciter:",noResume: "No previous reading",defaultSurah: "Choose a Surah and start reciting",sourceLabel: "Source: ",rights: "© 2026 Zikra — All rights reserved",memories: [{ text: "Indeed, with hardship is ease.", source: "Surah Ash-Sharh, 94:5", benefit: "Hardship does not last forever; ease follows." }, { text: "Actions are judged by intentions.", source: "Sahih al-Bukhari 1; Sahih Muslim 1907", benefit: "Sincerity of intention is central to good deeds." }, { text: "By the remembrance of God hearts are assured.", source: "Surah Ar-Ra'd, 13:28", benefit: "Remembering God brings inner peace." }, { text: "Whoever does not show mercy will not receive mercy.", source: "Sahih al-Bukhari 6013; Sahih Muslim 2319", benefit: "Compassion to others matters." }, { text: "My Lord, increase me in knowledge.", source: "Surah Ta-Ha, 20:114", benefit: "Seek beneficial knowledge." }]},
  de: {title: "Zikra",subtitle: "Ruhe für das Herz, jeden Tag",quranTitle: "Koran-Index",reciterLabel: "Rezitator wählen:",noResume: "Kein Leseverlauf",defaultSurah: "Sure wählen und Rezitation starten",sourceLabel: "Quelle: ",rights: "© 2026 Zikra — Alle Rechte vorbehalten",memories: [{ text: "Mit der Erschwernis kommt Erleichterung.", source: "Sure Asch-Scharh, 94:5", benefit: "Schwierigkeiten sind nicht von Dauer." }, { text: "Die Taten werden nach den Absichten bemessen.", source: "Sahih al-Bukhari 1; Sahih Muslim 1907", benefit: "Eine aufrichtige Absicht ist wichtig." }, { text: "Durch Gottes Gedenken kommen die Herzen zur Ruhe.", source: "Sure Ar-Ra'd, 13:28", benefit: "Gedenken an Gott schenkt inneren Frieden." }, { text: "Wer keine Barmherzigkeit zeigt, dem wird keine erwiesen.", source: "Sahih al-Bukhari 6013; Sahih Muslim 2319", benefit: "Sei barmherzig zu anderen." }, { text: "Mein Herr, mehre mein Wissen.", source: "Sure Ta-Ha, 20:114", benefit: "Strebe nach nützlichem Wissen." }]}
};

async function fetchJSON(url) {const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),15000);try{const r=await fetch(url,{signal:controller.signal});if(!r.ok)throw Error('HTTP '+r.status);return await r.json()}finally{clearTimeout(timer)}}

let prayerSchedule = [],prayerDataDate = '',prayerLoading = false,prayerRequest = 0,lastPrayerLoadAttempt = 0,prayerNextMarker = '',prayerSnapshot = null,prayerConfig = '';

function prayerPreferences() {return ZikraLocation.read();}

async function loadPrayerTimes() {
  const request = ++prayerRequest;prayerLoading = true;lastPrayerLoadAttempt = appNow();const config = JSON.stringify(prayerPreferences());if (config !== prayerConfig) {prayerConfig = config;prayerTimeZone = localStorage.getItem('zikra_timezone') || prayerTimeZone;prayerSnapshot = { zone: prayerTimeZone || Intl.DateTimeFormat().resolvedOptions().timeZone, date: '', timings: {}, schedule: [], error: 'جارٍ تحميل مواقيت الصلاة…' };prayerTimesObj = {};prayerSchedule = [];prayerDataDate = '';prayerNextMarker = '';updatePrayerCountdowns();window.zikraBroadcastContext?.();}
  syncNativeAdhan();
  try {
    const snapshot = await ZikraPrayer.load(prayerPreferences());if (request !== prayerRequest) return;
    prayerSnapshot = snapshot;prayerTimeZone = snapshot.zone;prayerDataDate = snapshot.date;prayerTimesObj = snapshot.timings;prayerSchedule = snapshot.schedule;prayerNextMarker = '';
    const h = snapshot.hijri;if (h) $('hijriText').textContent = ZikraI18n.t(lang === 'ar' ? h.day + ' ' + (h.month.ar || h.month.en) + ' ' + h.year + ' هـ' : h.day + ' ' + h.month.en + ' ' + h.year + ' AH');
  } catch (error) {if (request !== prayerRequest) return;prayerSnapshot = { zone: prayerTimeZone || Intl.DateTimeFormat().resolvedOptions().timeZone, date: '', timings: {}, schedule: [], error: error.message };prayerTimesObj = {};prayerSchedule = [];prayerDataDate = '';prayerNextMarker = '';} finally
  {if (request === prayerRequest) {prayerLoading = false;updatePrayerCountdowns();updateClock();syncNativeAdhan();window.zikraBroadcastContext?.();}}
}

function updatePrayerCountdowns() {
  if (!prayerSnapshot) return;const now = appNow(),model = ZikraPrayer.display(prayerSnapshot, now),next = model.next;
  if (prayerDataDate && ZikraPrayer.date(new Date(now), prayerSnapshot.zone) !== prayerDataDate && !prayerLoading && now - lastPrayerLoadAttempt > 60000) loadPrayerTimes();
  const marker = next ? next.key + '-' + next.date : 'none';
  if (marker !== prayerNextMarker) {prayerNextMarker = marker;for (const [key, id] of Object.entries({ Fajr: 'fajr', Sunrise: 'sunrise', Dhuhr: 'dhuhr', Asr: 'asr', Maghrib: 'maghrib', Isha: 'isha' })) {
      $(id).textContent = ZikraI18n.t((model.timings[key] || '--:--').split(' ')[0]);const card = $('box-' + key);card.classList.toggle('next-prayer', next?.key === key);card.classList.toggle('next-day', next?.key === key && !!model.nextDay);$('cd-' + key).textContent = ZikraI18n.t('');
    }}
  if (next) $('cd-' + next.key).textContent = ZikraI18n.t(ZikraPrayer.remaining(next.epoch - now));else if (prayerSnapshot.kind === 'location' && !prayerLoading && now - lastPrayerLoadAttempt > 60000) loadPrayerTimes();
}

let memoryIndex = 0;
window.zikraCurrentMemory=()=>{const m=T[lang].memories[memoryIndex];return {id:'home-'+memoryIndex,title:ZikraI18n.t('ذكرى اليوم'),text:$('memoryPreviewText').textContent,source:m.source,reflection:m.benefit,lesson:''}};

function renderMemory() {const m = T[lang].memories[memoryIndex];$("memoryPreviewText").textContent = ZikraI18n.t(m.text);$("memoryPreviewSource").textContent = ZikraI18n.t(T[lang].sourceLabel + m.source);$("memoryPreviewBenefit").textContent = ZikraI18n.t(m.benefit);window.ZikraFit?.memory($('memoryCard'));}

function nextMemory() {memoryIndex = (memoryIndex + 1) % T[lang].memories.length;renderMemory();}

function qmsg(s){statusBox.textContent=ZikraI18n.t(s);$('quranModal').classList.toggle('has-quran-status',!!s)}

function updateMiniAyah(ayah){const badge=$('miniAyah'),number=Math.max(1,Math.trunc(Number(ayah)||1));badge.dataset.ayah=String(number);badge.dataset.digits=String(number).length;badge.textContent=quranNumber(number);badge.setAttribute('aria-label',(lang==='ar'?'الآية ':lang==='de'?'Vers ':'Ayah ')+badge.textContent);if(badge.hidden)badge.hidden=false}

function quranTrackLabel(){return loaded?$('miniSurah').textContent+' · '+$('miniReader').textContent+' · '+(lang==='ar'?'الآية ':lang==='de'?'Vers ':'Ayah ')+quranNumber(currentAyahNum):ZikraI18n.t('اختر سورة وابدأ التلاوة')}
function renderQuranPlayIcon(button){const state=!loaded?'idle':audio.paused?'paused':'playing';if(button.dataset.playbackState!==state){button.dataset.playbackState=state;let path=button.querySelector('path');if(!path){button.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path/></svg>';path=button.querySelector('path')}path.setAttribute('d',state==='playing'?'M6 5h4v14H6zM14 5h4v14h-4z':'M8 5v14l11-7z')}const pressed=String(loaded&&!audio.paused);if(button.getAttribute('aria-pressed')!==pressed)button.setAttribute('aria-pressed',pressed)}

function updatePlayButton(){const label=ZikraI18n.t(audio.paused?'تشغيل القرآن':'إيقاف القرآن');for(const id of ['miniPlay','modalPlay']){const button=$(id);if(!button)continue;renderQuranPlayIcon(button);if(button.getAttribute('aria-label')!==label)button.setAttribute('aria-label',label)}for(const id of ['modalPlay','modalPrev','modalNext']){const button=$(id);if(button&&button.disabled===loaded)button.disabled=!loaded}const track=quranTrackLabel();if($('modalTrack').textContent!==track)$('modalTrack').textContent=track}

let currentAyahNum = 1,currentAyahs = [],quranLoadSequence = 0,pendingQuranSeek = false;

function quranPageLabel(number){return '⦅ '+(lang==='ar'?'صـ':lang==='de'?'S.':'p.')+' '+quranNumber(number)+' ⦆'}
function quranNumber(number){const key=lang==='ar'?'ar-u-nu-arab':'en';if(!quranNumberFormats.has(key))quranNumberFormats.set(key,new Intl.NumberFormat(key,{useGrouping:false}));return quranNumberFormats.get(key).format(number)}

function getPlayedSurahs() {try {return JSON.parse(localStorage.getItem('played_surahs') || '[]');} catch (_) {return [];}}

function markSurahAsPlayed(number) {try {localStorage.setItem('played_surahs', JSON.stringify([...new Set([...getPlayedSurahs(), number])]));} catch (_) {}}

function getReadBookmark() {
  try {
    const b = JSON.parse(localStorage.getItem('quran_bookmark') || 'null');
    if (!b || !Number.isInteger(Number(b.surahNum)) || b.surahNum < 1 || b.surahNum > 114) return null;
    return { ...b, surahNum: Number(b.surahNum), readerId: Number(b.readerId), ayahNum: Math.max(1, Math.trunc(Number(b.ayahNum) || 1)), time: Math.max(0, Number(b.time) || 0) };
  } catch (_) {return null;}
}

function saveReadBookmark(surahNum,readerId,ayahNum,time,force=false){const now=Date.now(),identity=[surahNum,readerId,ayahNum].join('|');if(!force&&now-lastBookmarkTime<5000&&lastBookmarkIdentity===identity)return;const verse=currentAyahs.find(v=>v.numberInSurah===ayahNum),b={surahNum,readerId,ayahNum,time:Math.max(0,Number(time)||0),page:Number(verse?.page)||1,surahName:$('miniSurah').textContent,readerName:read?.name||'',updated:now};try{localStorage.setItem('quran_bookmark',JSON.stringify(b));lastBookmarkTime=now;lastBookmarkIdentity=identity}catch(_){return}checkResumeBookmark()}

function flushReadBookmark() {if (loaded && !pendingQuranSeek) saveReadBookmark(currentSurahNum, read?.id, currentAyahNum, audio.currentTime, true);}

function checkResumeBookmark() {
  const b=getReadBookmark(),button=$('resumeLastReadBtn'),signature=[lang,b?.surahNum,b?.readerId,b?.ayahNum,$('surahIndexGrid').childElementCount].join('|');if(signature===bookmarkViewSignature)return;bookmarkViewSignature=signature;
  button.disabled = !b;
  if (!b) {button.textContent = ZikraI18n.t(T[lang].noResume);return;}
  const name=quranSurahLabel(b.surahNum);
  button.textContent = ZikraI18n.t((lang === 'ar' ? 'متابعة ' : lang==='de'?'Fortsetzen ':'Resume ') + name + ' · ' + (lang === 'ar' ? 'الآية ' : lang==='de'?'Vers ':'Ayah ') + quranNumber(b.ayahNum));
  button.onclick = () => {if(loaded){showQuranMushaf();audio.play().catch(()=>{})}else restoreReadBookmark(true,true)};
  $('surahIndexGrid').querySelectorAll('.surah-item').forEach((item) => {
    const selected = Number(item.dataset.number) === b.surahNum;
    item.classList.toggle('has-bookmark', selected);
    const progress = item.querySelector('.surah-last-position');
    if (progress) {progress.hidden = !selected;progress.textContent = ZikraI18n.t(selected ? (lang === 'ar' ? 'وصلت إلى الآية ' : 'Ayah ') + quranNumber(b.ayahNum) : '');}
  });
}

async function restoreReadBookmark(autoplay, showVerses) {
  const b = getReadBookmark();if (!b) return;
  const savedReader = readers.find((r) => Number(r.id) === b.readerId);
  if (savedReader) $('reciter').value = String(savedReader.id);
  renderReciterPicker();
  await loadSurahAndShow(b.surahNum, b.ayahNum, savedReader ? b.time : null, { autoplay, showVerses });
}



let renderedReadingPage=null,renderedReadingLanguage=null,readingPaneHeight=0,readingCenterFrame=0,readingTarget=null,verseBoundaryTimer=null,verseBoundaryKey='',quranClockRunning=false;
function setQuranView(reading){closeReciterPicker();$('quranModal').classList.toggle('quran-reading',reading);$('versesView').style.display=reading?'block':'none';$('surahIndexGrid').style.display=reading?'none':'grid';$('backToIndex').hidden=!reading;if(reading){prepareReadingCenter();centerQuranAyah($('a'+currentAyahNum));$('modalPlay').focus({preventScroll:true})}else $('reciterPickerButton').focus({preventScroll:true});window.ZikraNavigation?.invalidate()}
function cancelVerseBoundary(){if(verseBoundaryTimer!==null)clearTimeout(verseBoundaryTimer);verseBoundaryTimer=null;verseBoundaryKey=''}
function armVerseBoundary(next){if(!quranClockRunning||audio.paused||next===null||next===undefined){cancelVerseBoundary();return}const rate=audio.playbackRate>0?audio.playbackRate:1,key=next+'|'+rate;if(key===verseBoundaryKey)return;cancelVerseBoundary();verseBoundaryKey=key;const delay=Math.max(20,(next-audio.currentTime*1000)/rate+10);verseBoundaryTimer=setTimeout(()=>{verseBoundaryKey='';verseBoundaryTimer=null;mark()},Math.min(delay,2147480000))}
function stopQuranClock(){quranClockRunning=false;cancelVerseBoundary()}

let pendingReaderChange=null;
function quranSelectableReaders(){const timed=readers.filter(reader=>reader.timingAvailable);if(!timed.length)return readers;const preserved=read?.id||getReadBookmark()?.readerId;return readers.filter(reader=>reader.timingAvailable||reader.id===preserved)}
function showQuranIndex(){setQuranView(false)}
function prepareReadingCenter(){const box=$('verses'),height=box.clientHeight;if(height&&height!==readingPaneHeight){readingPaneHeight=height;box.style.setProperty('--reading-center-pad',height/2+'px')}}
function centerQuranAyah(element){readingTarget=element;cancelAnimationFrame(readingCenterFrame);readingCenterFrame=requestAnimationFrame(()=>{const target=readingTarget,box=$('verses');if(!target?.isConnected||$('versesView').style.display!=='block'||!$('quranModal').classList.contains('open'))return;prepareReadingCenter();const a=target.getBoundingClientRect(),b=box.getBoundingClientRect(),delta=(a.top+a.bottom)/2-(b.top+box.clientTop+box.clientHeight/2);if(Math.abs(delta)>1)box.scrollTop+=delta})}
function showQuranMushaf(){setQuranView(true)}
function chooseQuranReader(reader){closeReciterPicker();if(!loaded){$('reciter').value=String(reader.id);renderReciterPicker();renderSurahIndex();showQuranIndex();return}if(Number(read?.id)===reader.id){showQuranIndex();return}pendingReaderChange={reader,previous:$('reciter').value};$('readerChoiceName').textContent=quranReaderLabel(reader).name;$('readerChoiceStatus').textContent='';$('quranReaderDialog').hidden=false;$('quranModal').inert=true;document.body.classList.add('reader-confirm-open');ZikraI18n.apply($('quranReaderDialog'));$('readerResume').focus({preventScroll:true});window.ZikraNavigation?.invalidate()}
function hideReaderChange(){pendingReaderChange=null;$('quranReaderDialog').hidden=true;$('quranModal').inert=false;document.body.classList.remove('reader-confirm-open');window.ZikraNavigation?.invalidate()}
function cancelReaderChange(){if(!pendingReaderChange)return;$('reciter').value=pendingReaderChange.previous;hideReaderChange();$('reciterPickerButton').focus({preventScroll:true})}
async function applyReaderChange(index){if(!pendingReaderChange)return;const request=pendingReaderChange,choice=request.reader,surah=currentSurahNum;$('readerResume').disabled=true;$('readerIndex').disabled=true;$('readerChoiceStatus').textContent=ZikraI18n.t('جارٍ تجهيز الاستئناف…');try{const timing=await fetchJSON(API+'/ayat_timing?surah='+surah+'&read='+choice.id);if(pendingReaderChange!==request)return;const target=currentAyahNum;if(currentSurahNum!==surah||!Array.isArray(timing)||!timing.some(value=>Number(value.ayah)===target))throw Error('timing');const data={...surahListCache.find(value=>value.number===surah),ayahs:currentAyahs};$('reciter').value=String(choice.id);hideReaderChange();renderReciterPicker();renderSurahIndex();if(index)showQuranIndex();await loadSurahAndShow(surah,target,null,{autoplay:true,showVerses:!index,timingRecords:timing,surahData:data});if(index)showQuranIndex();else showQuranMushaf()}catch(_){if(pendingReaderChange===request)$('readerChoiceStatus').textContent=ZikraI18n.t('تعذّر تجهيز توقيت الآيات للقارئ المختار. جرّب مجددًا أو أغلق للمتابعة بالقارئ الحالي.')}finally{$('readerResume').disabled=false;$('readerIndex').disabled=false}}
$('readerResume').addEventListener('click',()=>applyReaderChange(false));$('readerIndex').addEventListener('click',()=>applyReaderChange(true));$('readerCancel').addEventListener('click',cancelReaderChange);window.addEventListener('resize',()=>{if($('versesView').style.display==='block')centerQuranAyah($('a'+currentAyahNum))},{passive:true});
function quranReaderLabel(reader){if(!reader)return {name:'',rewaya:''};return window.ZIKRA_QURAN_LABELS?.[lang]?.readers[reader.id]||reader}
function quranSurahLabel(number){return window.ZIKRA_QURAN_LABELS?.[lang]?.surahs[number]||surahListCache.find(item=>item.number===number)?.[lang==='ar'?'name':'englishName']||String(number)}
function renderQuranLabels(){if(loaded){$('miniReader').textContent=quranReaderLabel(read).name;$('miniSurah').textContent=quranSurahLabel(currentSurahNum);setCurrentAyah(currentAyahNum);checkResumeBookmark();updatePlayButton()}if(readers.length)renderReciterPicker()}
function renderReaderOptions() {
  const select = $('reciter'),old = select.value;select.replaceChildren();
  quranSelectableReaders().forEach((reader) => {const option = document.createElement('option');option.value = String(reader.id);option.textContent = ZikraI18n.t(quranReaderLabel(reader).name + (quranReaderLabel(reader).rewaya ? ' — ' + quranReaderLabel(reader).rewaya : ''));select.append(option);});
  const preferred = readers.find((r) => /ماهر.*المعيقلي/.test(r.name) && r.timingAvailable) || readers.find((r) => /ماهر.*المعيقلي/.test(r.name)) || readers[0];
  select.value = readers.some((r) => String(r.id) === old) ? old : String(preferred?.id || '');
  renderReciterPicker();
}

function renderReciterPicker(){const menu=$('reciterPickerMenu'),button=$('reciterPickerButton'),select=$('reciter');menu.replaceChildren();quranSelectableReaders().forEach(reader=>{const item=document.createElement('button');item.type='button';item.className='reader-option';item.setAttribute('role','option');item.dataset.readerId=String(reader.id);item.setAttribute('aria-selected',String(String(reader.id)===select.value));const name=document.createElement('strong'),detail=document.createElement('small');name.textContent=quranReaderLabel(reader).name;detail.textContent=quranReaderLabel(reader).rewaya||'';item.append(name,detail);item.addEventListener('click',()=>chooseQuranReader(reader));menu.append(item)});const selected=readers.find(reader=>String(reader.id)===select.value);button.textContent=ZikraI18n.t(selected?quranReaderLabel(selected).name+' · '+quranReaderLabel(selected).rewaya:'اختر القارئ')}

function closeReciterPicker() {$('reciterPickerMenu').hidden = true;$('reciterPickerButton').setAttribute('aria-expanded', 'false');}

function openReciterPicker() {
  if (!readers.length) {qmsg('قائمة القرّاء ما زالت قيد التحميل.');return;}
  const menu = $('reciterPickerMenu');menu.hidden = false;$('reciterPickerButton').setAttribute('aria-expanded', 'true');
  const current = menu.querySelector('[aria-selected="true"]') || menu.firstElementChild;current?.focus({ preventScroll: true });current?.scrollIntoView({ block: 'nearest' });
}

$('reciterPickerButton').addEventListener('click', () => {$('reciterPickerMenu').hidden ? openReciterPicker() : closeReciterPicker();});

$('reciter').addEventListener('change', () => {renderReciterPicker();renderSurahIndex();});

document.addEventListener('click', (event) => {if (!event.target.closest('.reader-picker-wrap')) closeReciterPicker();});

async function loadReaders() {
  try {
    const results = await Promise.allSettled([fetchJSON(API + '/reciters?language=ar'), fetchJSON(API + '/ayat_timing/reads')]);
    const catalog = results[0].status === 'fulfilled' ? results[0].value.reciters : [],timed = results[1].status === 'fulfilled' ? results[1].value : [];
    const all = [];
    (catalog || []).forEach((reciter) => (reciter.moshaf || []).forEach((m) => {if (m.server && m.id) all.push({ id: Number(m.id), name: reciter.name, rewaya: m.name || '', folder_url: m.server, surah_list: String(m.surah_list || '') });}));
    (Array.isArray(timed) ? timed : []).forEach((reader) => {
      const match = all.find((r) => r.id === Number(reader.id));
      if (match) Object.assign(match, { name: reader.name || match.name, rewaya: reader.rewaya || match.rewaya, folder_url: reader.folder_url || match.folder_url, timingAvailable: true });else
      all.push({ ...reader, id: Number(reader.id), timingAvailable: true });
    });
    if (!all.length) throw Error('No readers');
    const seen = new Set();readers = all.filter((r) => !seen.has(r.id) && seen.add(r.id));
    const priority = (r) => /ماهر.*المعيقلي/.test(r.name) ? r.timingAvailable ? 0 : 1 : 2;
    readers.sort((a, b) => priority(a) - priority(b) || a.name.localeCompare(b.name, 'ar') || Number(!!b.timingAvailable) - Number(!!a.timingAvailable));
    renderReaderOptions();await fetchSurahsIndex();qmsg('اختر القارئ ثم السورة، أو تابع من آخر آية.');
    if(!loaded&&quranLoadSequence===0&&getReadBookmark())await restoreReadBookmark(false,false);
  } catch (_) {$('reciterPickerButton').textContent = ZikraI18n.t('تعذّر تحميل القرّاء');qmsg('تعذّر تحميل فهرس القرآن. تحقق من اتصال الإنترنت.');}
}

async function fetchSurahsIndex(){const result=await ZikraQuranData.load();surahListCache=result.surahs;renderSurahIndex()}

function renderSurahIndex() {bookmarkViewSignature='';
  const grid = $('surahIndexGrid'),selected = readers.find((r) => String(r.id) === $('reciter').value),available = selected?.surah_list ? new Set(selected.surah_list.split(',')) : null;
  grid.replaceChildren();
  surahListCache.forEach((surah) => {
    const item = document.createElement('button');item.type = 'button';item.className = 'surah-item';item.dataset.number = String(surah.number);item.disabled = !!available && !available.has(String(surah.number));
    const number = document.createElement('span');number.className = 'surah-num';number.dataset.digits=String(surah.number).length;number.textContent = ZikraI18n.t(quranNumber(surah.number));
    const copy = document.createElement('span');copy.className = 'surah-info';
    const title = document.createElement('strong');title.className = 'surah-title';title.textContent = quranSurahLabel(surah.number);
    const detail = document.createElement('small');detail.className = 'surah-meta';detail.textContent = ZikraI18n.t((surah.revelationType === 'Meccan' ? 'مكية' : 'مدنية') + ' · ' + quranNumber(surah.numberOfAyahs) + ' آية');
    const progress = document.createElement('small');progress.className = 'surah-last-position';progress.hidden = true;
    copy.append(title, detail, progress);item.append(number, copy);
    item.addEventListener('click', () => loadSurahAndShow(surah.number));grid.append(item);
  });checkResumeBookmark();
}

function renderVerses(ayahs,activeAyah=currentAyahNum) {
  const center=Number(ayahs[activeAyah-1]?.page)||1,pages=[...new Set(ayahs.map(v=>Number(v.page)||1))],at=pages.indexOf(center),visible=new Set(pages.slice(Math.max(0,at-1),at+2));renderedReadingPage=center;renderedReadingLanguage=lang;versesBox.dataset.page=String(center);
  versesBox.replaceChildren();let page = null,pageText = null;
  ayahs.filter(verse=>visible.has(Number(verse.page)||1)).forEach((verse) => {
    const pageNo = Number(verse.page) || 1;
    if (page !== pageNo) {page = pageNo;const sheet = document.createElement('section');sheet.className = 'mushaf-page';pageText = document.createElement('div');pageText.className = 'mushaf-page-text';const label = document.createElement('div');label.className = 'mushaf-page-number';label.textContent=quranPageLabel(pageNo);sheet.append(pageText, label);versesBox.append(sheet);}
    const presentation=ZikraQuranDisplay.opening(currentSurahNum,verse.numberInSurah,verse.text);let opening=null;if(presentation.basmala){opening=document.createElement('header');opening.className='surah-opening';const title=document.createElement('h3');title.className='surah-heading';title.textContent=quranSurahLabel(currentSurahNum);opening.append(title);pageText.before(opening);if(!presentation.numbered){const basmala=document.createElement('div');basmala.className='surah-basmala';basmala.textContent=ZikraQuranDisplay.ornament;opening.append(basmala);}}const span = document.createElement('span');span.className = presentation.numbered?'ayah surah-basmala':'ayah';span.id = 'a' + verse.numberInSurah;span.dataset.page = String(pageNo);span.dataset.ayah = String(verse.numberInSurah);span.setAttribute('aria-label', 'الآية ' + quranNumber(verse.numberInSurah) + '، ' + verse.text);
    span.append(document.createTextNode((presentation.numbered?ZikraQuranDisplay.ornament:presentation.text) + ' '));const marker = document.createElement('span');marker.className = 'waqf';marker.setAttribute('aria-hidden', 'true');marker.dataset.digits=String(verse.numberInSurah).length;const numeral=document.createElement('span');numeral.className='waqf-number';numeral.textContent=quranNumber(verse.numberInSurah);marker.append(numeral);span.append(marker);
    (presentation.numbered?opening:pageText).append(span, document.createTextNode(' '));
  });
}

function setCurrentAyah(number,scroll=false){const verse=currentAyahs[Number(number)-1];if(!verse||verse.numberInSurah!==Number(number))return;currentAyahNum=verse.numberInSurah;const redraw=renderedReadingPage!==(Number(verse.page)||1)||renderedReadingLanguage!==lang;if(redraw)renderVerses(currentAyahs,currentAyahNum);const element=$('a'+currentAyahNum),signature=[currentSurahNum,read?.id,lang,currentAyahNum].join('|');if(renderedAyahSignature===signature&&renderedAyahElement===element&&element?.isConnected)return;renderedAyahSignature=signature;renderedAyahElement=element;const old=versesBox.querySelector('.ayah.active');if(old!==element){old?.classList.remove('active');old?.removeAttribute('aria-current');element?.classList.add('active');element?.setAttribute('aria-current','true');if((scroll||redraw)&&$('quranModal').classList.contains('open')&&$('versesView').style.display==='block')centerQuranAyah(element)}updateMiniAyah(currentAyahNum);$('miniPage').textContent=quranPageLabel(verse.page||1);if($('miniPage').hidden)$('miniPage').hidden=false;$('miniVerse').textContent=ZikraQuranDisplay.opening(currentSurahNum,verse.numberInSurah,verse.text).text||verse.text;window.ZikraFit?.verse($('miniVerse')); $('modalTrack').textContent=quranTrackLabel()}

async function loadSurahAndShow(surahNum, targetAyah = 1, targetTime = null, options = {}) {
  const reader = readers.find((r) => String(r.id) === $('reciter').value);if (!reader) {qmsg('اختر القارئ أولًا.');return;}
  if (reader.surah_list && !reader.surah_list.split(',').includes(String(surahNum))) {qmsg('هذه السورة غير متوفرة لهذا القارئ.');return;}
  const sequence = ++quranLoadSequence;flushReadBookmark();loaded = false;pendingQuranSeek = true;cancelVerseBoundary();$('progressFill').style.transform='scaleX(0)';audio.pause();audio.onloadedmetadata = null;read = reader;currentSurahNum = Number(surahNum);times = [];closeReciterPicker();
  if(options.showVerses!==false)setQuranView(true);
  versesBox.textContent = ZikraI18n.t('جارٍ تحميل السورة…');qmsg('جارٍ تحميل السورة…');updatePlayButton();
  try {
    const [timing, result] = await Promise.all([options.timingRecords?Promise.resolve(options.timingRecords):reader.timingAvailable ? fetchJSON(API + '/ayat_timing?surah=' + surahNum + '&read=' + reader.id).catch(() => []) : Promise.resolve([]),options.surahData?Promise.resolve({data:options.surahData}):ZikraQuranData.load().then(result=>({data:result.surahs[Number(surahNum)-1]}))]);
    if (sequence !== quranLoadSequence) return;
    times=ZikraQuranTimeline.normalize(timing,result.data.ayahs.length);
    currentAyahs=result.data.ayahs;currentAyahNum=Math.max(1,Math.min(currentAyahs.length,Number(targetAyah)||1));renderVerses(currentAyahs,currentAyahNum);
    $('miniSurah').textContent = quranSurahLabel(surahNum);$('miniReader').textContent = quranReaderLabel(reader).name;loaded = true;setCurrentAyah(currentAyahNum);if(options.showVerses!==false)prepareReadingCenter();renderReciterPicker();updatePlayButton();
    if (options.showVerses !== false && $('quranModal').classList.contains('open')) $('modalPlay').focus({ preventScroll: true });
    const timingForAyah = times.find((t) => Number(t.ayah) === currentAyahNum);
    const seekTime = targetTime !== null ? Math.max(0, Number(targetTime) || 0) : currentAyahNum > 1 && timingForAyah ? Number(timingForAyah.start_time) / 1000 : 0;
    audio.onloadedmetadata = () => {
      if (sequence !== quranLoadSequence) return;
      audio.currentTime = Number.isFinite(audio.duration) ? Math.min(seekTime, Math.max(0, audio.duration - .1)) : seekTime;
      pendingQuranSeek = false;setCurrentAyah(currentAyahNum);if(options.showVerses!==false)centerQuranAyah($('a'+currentAyahNum));flushReadBookmark();
      if (options.autoplay !== false) audio.play().catch(() => qmsg('اضغط زر التشغيل لبدء التلاوة.'));
    };
    audio.preload = 'metadata';audio.src = String(reader.folder_url || reader.server).replace(/\/?$/, '/') + String(surahNum).padStart(3, '0') + '.mp3';audio.load();
    saveReadBookmark(currentSurahNum, reader.id, currentAyahNum, seekTime, true);
    qmsg(times.length ? '' : 'هذا التسجيل لا يوفّر توقيت الآيات؛ يمكنك اختيار تسجيل آخر لتحديد الآية أثناء التلاوة.');
  } catch (_) {if (sequence !== quranLoadSequence) return;loaded = false;pendingQuranSeek = false;versesBox.textContent = ZikraI18n.t('تعذّر تحميل السورة. تحقق من اتصال الإنترنت.');qmsg('تعذّر تحميل السورة. جرّب مرة أخرى.');updatePlayButton();}
}

function mark(){if(!loaded||pendingQuranSeek)return;const position=ZikraQuranTimeline.position(times,audio.currentTime,audio.duration);if(position){setCurrentAyah(position.ayah,true);const progress='scaleX('+position.progress.toFixed(4)+')';if($('progressFill').style.transform!==progress)$('progressFill').style.transform=progress;const percent=String(Math.round(position.progress*100)),bar=$('progressFill').parentElement;if(bar.getAttribute('aria-valuenow')!==percent)bar.setAttribute('aria-valuenow',percent);window.ZikraFit?.verseProgress($('miniVerse'),position.progress);armVerseBoundary(position.next)}else cancelVerseBoundary();saveReadBookmark(currentSurahNum,read?.id,currentAyahNum,audio.currentTime)}

function seekToAyah(number, play) {
  if (pendingQuranSeek) {qmsg("جارٍ تجهيز التسجيل…");return;}
  const timing = times.find((t) => Number(t.ayah) === Number(number));if (!timing) {qmsg('لا يتوفر توقيت هذه الآية في التسجيل المحدد.');return;}
  audio.currentTime = Number(timing.start_time) / 1000;setCurrentAyah(number, true);flushReadBookmark();if (play) audio.play().catch(() => {});
}

function jump(delta) {if (!times.length) {qmsg('اختر تسجيلًا يدعم تحديد الآيات.');return;}seekToAyah(Math.max(1, Math.min(currentAyahs.length, currentAyahNum + delta)), true);}

function toggleAudio() {if (!loaded) {openModal('quranModal');return;}audio.paused ? audio.play().catch(() => qmsg('اضغط التشغيل مجددًا.')) : audio.pause();}

$('miniPlay').addEventListener('click', toggleAudio);

$('modalPlay').addEventListener('click', toggleAudio);

$('modalPrev').addEventListener('click', () => jump(-1));

$('modalNext').addEventListener('click', () => jump(1));

$('miniPrev').addEventListener('click', () => loaded ? jump(-1) : openModal('quranModal'));

$('miniNext').addEventListener('click', () => loaded ? jump(1) : openModal('quranModal'));

$('backToIndex').addEventListener('click', showQuranIndex);

audio.addEventListener('timeupdate', mark);

audio.addEventListener('play', updatePlayButton);

audio.addEventListener('pause',()=>{stopQuranClock();updatePlayButton();flushReadBookmark()});

audio.addEventListener('seeked',()=>{cancelVerseBoundary();quranClockRunning=!audio.paused&&audio.readyState>=2;mark()});
audio.addEventListener('playing',()=>{quranClockRunning=true;mark()});
audio.addEventListener('waiting',stopQuranClock);audio.addEventListener('seeking',stopQuranClock);audio.addEventListener('ratechange',()=>{cancelVerseBoundary();mark()});

window.addEventListener('pagehide',()=>{stopQuranClock();flushReadBookmark()});

document.addEventListener('visibilitychange', () => {if (document.hidden) flushReadBookmark();});

let autoNextInProgress = false;

audio.addEventListener('ended', async () => {stopQuranClock();if (autoNextInProgress) return;flushReadBookmark();markSurahAsPlayed(currentSurahNum);autoNextInProgress = true;try {if (currentSurahNum < 114) {$('reciter').value = String(read.id);await loadSurahAndShow(currentSurahNum + 1);} else qmsg('انتهت تلاوة القرآن الكريم.');} finally {autoNextInProgress = false;}});

window.handleAndroidMediaKey = function (command) {
  const action = String(command || "").toLowerCase();
  try {
    switch (action) {
      case "play":
        if (loaded) {audio.play().catch(() => qmsg(lang === "ar" ? "تعذّر التشغيل. اضغط OK ثم حاول مجدداً." : "Playback could not start. Press OK and try again."));} else
        {openModal("quranModal");qmsg(lang === "ar" ? "اختر القارئ والسورة لبدء التلاوة." : "Choose a reciter and surah to begin.");}
        break;
      case "pause":
        audio.pause();
        updatePlayButton();
        break;
      case "toggle":
        toggleAudio();
        break;
      case "stop":
        audio.pause();
        try {audio.currentTime = 0;} catch (e) {}
        updatePlayButton();
        break;
      case "next":
        if (loaded && times.length) jump(1);else
        {openModal("quranModal");qmsg(lang === "ar" ? "اختر السورة أولاً." : "Choose a surah first.");}
        break;
      case "previous":
        if (loaded && times.length) jump(-1);else
        {openModal("quranModal");qmsg(lang === "ar" ? "اختر السورة أولاً." : "Choose a surah first.");}
        break;
      default:
        console.debug("Zikra: unsupported media command", action);
    }
  } catch (e) {console.error("Zikra media command failed:", action, e);}
};

let lastHomeBackAt = 0,exitPromptOpen = false;

function showExitPrompt() {exitPromptOpen = true;openModal("exitModal");$("cancelExitBtn").focus();}

function hideExitPrompt() {exitPromptOpen = false;closeModal("exitModal");}

$("cancelExitBtn").addEventListener("click", hideExitPrompt);

$("confirmExitBtn").addEventListener("click", () => {exitPromptOpen = false;flushReadBookmark();if (window.AndroidTV && typeof window.AndroidTV.exitApp === "function") window.AndroidTV.exitApp();else closeModal("exitModal");});

$("exitModal").addEventListener("click", (e) => {if (e.target === $("exitModal")) hideExitPrompt();});

const ADHAN_AUDIO_URLS = {
  makkah: "https://www.islamcan.com/audio/adhan/azan1.mp3",
  madinah: "https://www.islamcan.com/audio/adhan/azan2.mp3",
  alafasy: "https://www.islamcan.com/audio/adhan/azan8.mp3",
  syria: "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Adhan_Syria_(أذان_سوريا).mp3"
};

const ADHAN_PRAYERS = ["Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"];

const ADHAN_DEFAULTS = Object.fromEntries(ADHAN_PRAYERS.map((k) => [k, { enabled: true, voice: k === "Fajr" ? "makkah" : "makkah" }]));

const ADHAN_STORAGE_KEY = "zikra_adhan_settings_v1";

function loadAdhanSettings() {
  try {const saved = JSON.parse(localStorage.getItem(ADHAN_STORAGE_KEY) || "null");const out = {};for (const k of ADHAN_PRAYERS) {const v = saved && saved[k];out[k] = { enabled: typeof v?.enabled === "boolean" ? v.enabled : true, voice: ADHAN_AUDIO_URLS[v?.voice] ? v.voice : "makkah" };}return out;} catch (e) {return JSON.parse(JSON.stringify(ADHAN_DEFAULTS));}
}

function syncNativeAdhan(){if(!window.AndroidTV?.setAdhanSchedule)return;const p=prayerPreferences();window.AndroidTV.setAdhanSchedule(JSON.stringify({config:{backgroundEnabled:localStorage.getItem('zikra_adhan_background')!=='false',city:p.city,lat:p.lat,lon:p.lon,method:p.method||3,mosque:p.mosque?.id||'',zone:prayerSnapshot?.zone||p.zone,kind:prayerSnapshot?.kind||'',language:lang},settings:adhanSettings,schedule:prayerSchedule}));}
function nativeAdhan(key){const old=window.zikraAdhanActive;if(old===key)return;if(old)$('box-'+old)?.classList.remove('adhan-active');window.zikraAdhanActive=key||null;if(key){window.zikraStopBrandDua?.(false);audio.pause();flushReadBookmark();window.zikraPreviewAdhan?.(null,false);$('box-'+key)?.classList.add('adhan-active');}window.zikraBroadcastContext?.();}
window.addEventListener('zikra:adhan-native',event=>nativeAdhan(event.detail?.active?event.detail.key:null));
window.addEventListener('zikra:runtime',event=>{if(typeof event.detail?.adhanPrayer==='string')nativeAdhan(event.detail.adhanPrayer||null)});
window.zikraStopAdhan=()=>{clearTimeout(adhanTimer);adhanAudio.pause();adhanAudio.removeAttribute('src');adhanAudio.load();window.AndroidTV?.stopAdhan?.();nativeAdhan(null);};
function checkAutomaticAdhan(){
  if(window.AndroidTV?.setAdhanSchedule)return;const now=appNow();
  for(const entry of prayerSchedule){if(now-entry.epoch<0||now-entry.epoch>=60000)continue;const marker="zikra_adhan_played_"+entry.date+"_"+entry.key;if(localStorage.getItem(marker)==="1")continue;localStorage.setItem(marker,"1");const cfg=adhanSettings[entry.key];if(!cfg?.enabled)continue;
    clearTimeout(adhanTimer);adhanAudio.pause();nativeAdhan(entry.key);const current=adhanAudio=new Audio(ADHAN_AUDIO_URLS[cfg.voice]||ADHAN_AUDIO_URLS.makkah);
    const finish=()=>{if(current===adhanAudio){clearTimeout(adhanTimer);current.pause();nativeAdhan(null)}};
    current.addEventListener('ended',finish,{once:true});current.addEventListener('error',finish,{once:true});adhanTimer=setTimeout(finish,240000);current.play().catch(finish);
  }
}

let adhanSettings = loadAdhanSettings(),adhanAudio = new Audio(),adhanTimer;
const clockFormats = new Map();let clockStamp = '',quranPromise = null;
function updateClock() {
  const zone = prayerTimeZone || ZikraLocation.read().zone,key = lang + '|' + zone;
  if (!clockFormats.has(key)) {const locale = lang === 'ar' ? 'ar' : lang === 'de' ? 'de-DE' : 'en-GB';clockFormats.set(key, { time: new Intl.DateTimeFormat(locale, { timeZone: zone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }), hijri: new Intl.DateTimeFormat(locale + '-u-ca-islamic-umalqura', { timeZone: zone, year: 'numeric', month: 'long', day: 'numeric' }), date: new Intl.DateTimeFormat(locale, { timeZone: zone, weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' }) });}
  const now = new Date(appNow()),format = clockFormats.get(key),stamp = key + '|' + Math.floor(appNow() / 60000);if (stamp === clockStamp) return;clockStamp = stamp;$('clock').textContent = ZikraI18n.t(format.time.format(now));$('dateText').textContent = ZikraI18n.t(format.date.format(now));$('hijriText').textContent = ZikraI18n.t(format.hijri.format(now));
}
function updateCityLabel() {$('cityLabel').textContent = ZikraI18n.t(city.name);}
function ensureQuranData() {if(!quranPromise){ZikraQuranData.load().catch(()=>{});quranPromise=loadReaders().finally(()=>{if(!readers.length)quranPromise=null})}return quranPromise;}
function openModal(id){window.zikraStopBrandDua?.();previousFocus=document.activeElement;$(id).classList.add('open');document.querySelector('.app').inert=true;if(id==='quranModal'){ensureQuranData();if(loaded&&!audio.paused)showQuranMushaf();else showQuranIndex()}else $(id).querySelector('button')?.focus({preventScroll:true})}
function closeModal(id) {if (id === 'quranModal') closeReciterPicker();$(id).classList.remove('open');document.querySelector('.app').inert = false;if (previousFocus?.isConnected) previousFocus.focus({ preventScroll: true });}
function renderWeather() {if (!weatherData) {$('weatherCondition').textContent=ZikraI18n.t(weatherError||'جارٍ تحميل الطقس…');return;}const d = weatherData,c = d.current,info = ZikraWeatherScene.describe(c.weather_code, c.is_day);$('weatherIcon').innerHTML = ZikraWeatherScene.icon(c.weather_code, c.is_day);$('weatherTemp').textContent = ZikraI18n.t(Math.round(c.temperature_2m) + '°');$('weatherCondition').textContent = ZikraI18n.t(info.label);$('weatherExtra').textContent = ZikraI18n.t('↑ ' + Math.round(d.daily.temperature_2m_max[0]) + '°  ↓ ' + Math.round(d.daily.temperature_2m_min[0]) + '° · ' + Math.round(c.apparent_temperature) + '° ' + (lang === 'ar' ? 'محسوسة' : lang === 'de' ? 'gefühlt' : 'feels like'));ZikraWeatherScene.mount($('weatherTileEffects'), c.weather_code, c.is_day);}
let weatherRequest = 0,lastWeatherLoad = 0;
async function loadWeather() {lastWeatherLoad = appNow();const id = ++weatherRequest;try {const d = await ZikraWeatherData.load(ZikraLocation.read());if (id !== weatherRequest) return;weatherData = d;weatherError = '';renderWeather();window.zikraBroadcastContext?.();} catch (error) {if (id === weatherRequest) {weatherError=error.message;renderWeather();}}}
let appliedLanguage = null;
function changeLanguage(value) {if (appliedLanguage === value) return;appliedLanguage = value;if (!['ar', 'de', 'en'].includes(value)) value = 'ar';lang = value;localStorage.setItem('zikra_language', value);document.documentElement.lang = value;ZikraI18n.set(value);const t = T[value];$('appTitle').textContent = ZikraI18n.t(t.title);$('subtitle').textContent = ZikraI18n.t(t.subtitle);$('quranTitle').textContent = ZikraI18n.t(t.quranTitle);$('reciterLabel').textContent = ZikraI18n.t(t.reciterLabel);$('footerRights').textContent = ZikraI18n.t(t.rights);document.querySelectorAll('.p-name').forEach((node) => {node.textContent = ZikraI18n.t(node.dataset[value] || node.dataset.ar);});if (!loaded) {$('miniSurah').textContent = ZikraI18n.t(t.defaultSurah);$('miniVerse').textContent = ZikraI18n.t('');}renderMemory();updateClock();if (surahListCache.length) renderSurahIndex();renderQuranLabels();updatePlayButton();updateCityLabel();renderWeather();if(prayerSnapshot){prayerNextMarker='';updatePrayerCountdowns()}ZikraI18n.apply();}
window.zikraHomeBack = () => {if(window.zikraStopBrandDua?.())return;if(pendingReaderChange){cancelReaderChange();return}
  if (!$('reciterPickerMenu').hidden) {closeReciterPicker();$('reciterPickerButton').focus({ preventScroll: true });return;}
  if($('quranModal').classList.contains('open')){if($('versesView').style.display==='block')showQuranIndex();else closeModal('quranModal');return}

  if (exitPromptOpen) {hideExitPrompt();return;}const now = Date.now();if (now - lastHomeBackAt < 2200) {lastHomeBackAt = 0;showExitPrompt();} else lastHomeBackAt = now;
};
window.zikraApplySavedSettings = () => {const p = ZikraLocation.read(),changed = city.latitude !== p.lat || city.longitude !== p.lon;city = { name: p.city, latitude: p.lat, longitude: p.lon };updateCityLabel();adhanSettings = loadAdhanSettings();changeLanguage(localStorage.getItem('zikra_language') || lang);loadPrayerTimes();if (changed) {weatherData = null;loadWeather();}};
window.zikraPrayerSnapshot = () => prayerSnapshot;
$('closeQuran').addEventListener('click', () => closeModal('quranModal'));
window.addEventListener('blur', () => document.body.dataset.active = 'false');window.addEventListener('focus', () => document.body.dataset.active = 'true');document.body.dataset.active = 'true';
let lastMemoryTick = appNow(),lastRuntimeCheck=-Infinity;function tick() {const now = appNow();if (performance.now()-lastRuntimeCheck>=60000) {lastRuntimeCheck=performance.now();ZikraRuntime.refresh();window.AndroidTV?.checkForUpdate?.();}updateClock();updatePrayerCountdowns();checkAutomaticAdhan();if (now - lastMemoryTick >= 30000) {lastMemoryTick = now;nextMemory();}if (now - lastWeatherLoad >= 15 * 60 * 1000) loadWeather();if (prayerSnapshot?.error && !prayerLoading && now - lastPrayerLoadAttempt > 60000 && ZikraLocation.valid(city.latitude, city.longitude)) loadPrayerTimes();}
window.addEventListener('online', () => {loadPrayerTimes();loadWeather();});
ZikraLocation.save(ZikraLocation.read());localStorage.removeItem('zikraSettings');localStorage.removeItem('zikra_weather_cache_v2');localStorage.removeItem('zikra_preferred_mosque');for (const key of Object.keys(localStorage)) {if (key.startsWith('zikra_adhan_played_') && key.slice(19, 29) < new Date(Date.now() - 7 * 86400000).toISOString().slice(0, 10)) localStorage.removeItem(key);}
changeLanguage(lang);checkResumeBookmark();loadPrayerTimes();loadWeather();setInterval(tick, 1000);
if (location.hash === '#quran') openModal('quranModal');

ZikraLocation.refreshAuthorizedGPS().then((p) => {if (p) {window.zikraApplySavedSettings();window.zikraBroadcastContext?.();}});

let previewAudio = new Audio(),previewWasPlaying = false;window.zikraPreviewAdhan = (voice, resume = true) => {if(voice&&window.zikraAdhanActive)return;previewAudio.pause();if (!voice) {previewAudio.removeAttribute('src');previewAudio.load();if (previewWasPlaying && resume && !window.zikraAdhanActive) audio.play().catch(() => {});previewWasPlaying = false;return;}if (!previewWasPlaying) previewWasPlaying = !audio.paused;audio.pause();if (ADHAN_AUDIO_URLS[voice]) {previewAudio.src = ADHAN_AUDIO_URLS[voice];previewAudio.play().catch(() => {});}};
window.addEventListener('zikra:runtime', () => {clockStamp = '';updateClock();updatePrayerCountdowns();window.zikraBroadcastContext?.();});
audio.addEventListener('play',()=>{if(window.zikraAdhanActive)audio.pause()});

window.zikraRenderInterface=()=>{clockStamp='';updateClock();renderWeather();renderMemory();updatePlayButton();updatePrayerCountdowns();window.ZikraFit?.all()};

// A local font finishing its first load must not leave the active verse off-center.
document.fonts?.addEventListener('loadingdone',()=>{ZikraFit?.verse($('miniVerse'));if($('quranModal').classList.contains('quran-reading'))centerQuranAyah($('a'+currentAyahNum));});
