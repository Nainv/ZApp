/* Build both targets from the same interface; Android sources remain untouched. */
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const out = path.resolve(process.argv[2] || path.join(root, '..', 'webos-build'));
if (out === root || root.startsWith(out + path.sep)) throw Error('Unsafe output directory');
if (fs.existsSync(out)) throw Error('Use a new, empty output directory');
const number = Number(process.env.GITHUB_RUN_NUMBER || 1);
if (!Number.isSafeInteger(number) || number < 1 || number > 999999) throw Error('Invalid build number');
const version = '1.' + Math.floor(number / 1000) + '.' + number % 1000;
const hosted = process.env.ZIKRA_WEBOS_URL || 'https://koukach91-hash.github.io/zikra-android-tv-new/lg/';
const url = new URL(hosted);
if (url.protocol !== 'https:' || url.search || url.hash || !hosted.endsWith('/')) throw Error('Expected an HTTPS directory URL');
const app = path.join(out, 'app'), site = path.join(out, 'site');
fs.mkdirSync(site, {recursive:true});
for (const name of ['index.html','assets','cells','favicon.ico','favicon.png','apple-touch-icon.png','site.webmanifest','web-app-icon-192.png']) {
  fs.cpSync(path.join(root,name), path.join(site,name), {recursive:true});
}
const build = {version, build:number, source:process.env.GITHUB_SHA || 'local', publishedAt:new Date().toISOString()};
fs.writeFileSync(path.join(site,'webos-build.json'), JSON.stringify(build));
fs.copyFileSync(path.join(__dirname,'bridge.js'),path.join(site,'assets/js/webos-bridge.js'));
function visit(directory) {
  for (const file of fs.readdirSync(directory,{withFileTypes:true})) {
    const full = path.join(directory,file.name);
    if (file.isDirectory()) visit(full);
    else if (file.name.endsWith('.html')) {
      const prefix = path.relative(path.dirname(full),site).split(path.sep).join('/');
      const script = (prefix ? prefix+'/' : '')+'assets/js/webos-bridge.js?v='+number;
      const html=fs.readFileSync(full,'utf8').replace(/\?v=\d+/g,'?v='+number).replace(/<script\b/gi,'<script type="text/zikra-script"');
      fs.writeFileSync(full,html.replace(/<head>/i,'<head><script src="'+script+'"></script>'));
    }
  }
}
visit(site);
// webOS Back/transport keys use numeric codes; the shared handler owns navigation.
const remote = path.join(site,'assets/js/tv-remote.js');
fs.writeFileSync(remote, fs.readFileSync(remote,'utf8').replace('event.keyCode===4','(event.keyCode===4||event.keyCode===461)'));
const runtime = path.join(site,'assets/js/home-runtime.js');
fs.writeFileSync(runtime,fs.readFileSync(runtime,'utf8').replace('if (window.AndroidTV && typeof window.AndroidTV.exitApp === "function") window.AndroidTV.exitApp();','if (window.ZikraWebOS) window.ZikraWebOS.exit();else if (window.AndroidTV && typeof window.AndroidTV.exitApp === "function") window.AndroidTV.exitApp();'));
const updater = path.join(site,'assets/js/app-updater.js');
// Never offer an Android installer to an LG user.
fs.writeFileSync(updater, `(() => {const button=document.getElementById('updateApp');if(button)button.addEventListener('click',()=>window.ZikraWebOS.refresh());})();\n`);
const settings = path.join(site,'assets/js/settings-hub.js');
let settingsText = fs.readFileSync(settings,'utf8');
settingsText = settingsText.replace("function showView(name){", "function showView(name){if(['startup','alerts'].includes(name)){window.ZikraWebOS.unavailable(name);return;}");
settingsText = settingsText.replace("button('تحديد موقعي الحالي',useLocation", "button('اختيار المدينة — LG',chooseCity");
settingsText = settingsText.replace("draft.mosque?.id==='erfurt_erfurter-moschee'?'جدول مسجد إرفورت المنشور، مع حساب احتياطي عند انقطاع الاتصال.':'المواقيت حسب الموقع المختار.'", "'المواقيت المحسوبة حسب الموقع المختار؛ تحديد المدينة يدوي في نسخة LG.'");
fs.writeFileSync(settings,settingsText);
// Cache-bust dynamically loaded data/styles as well as parser-loaded assets.
function stamp(directory){for(const file of fs.readdirSync(directory,{withFileTypes:true})){const full=path.join(directory,file.name);if(file.isDirectory())stamp(full);else if(/\.(js|css)$/.test(file.name))fs.writeFileSync(full,fs.readFileSync(full,'utf8').replace(/\?v=\d+/g,'?v='+number));}}
stamp(path.join(site,'assets'));
const homeFile=path.join(site,'index.html');
const icon=(content)=>'<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">'+content+'</svg>';
fs.writeFileSync(homeFile,fs.readFileSync(homeFile,'utf8')
 .replace('<span class="refresh-icon" aria-hidden="true">↻</span>','<span class="refresh-icon" aria-hidden="true">'+icon('<path d="M20 7v5h-5M20 12a8 8 0 1 0-2 6M20 12l-5-5"/>')+'</span>')
 .replaceAll('<span class="p-icon">☾</span>','<span class="p-icon">'+icon('<path d="M20 15a8.5 8.5 0 0 1-11-11 8.5 8.5 0 1 0 11 11Z"/>')+'</span>')
 .replaceAll('<span class="p-icon">◒</span>','<span class="p-icon">'+icon('<path d="M3 17h18M6 14a6 6 0 1 1 12 0M12 2v2M3 6l2 2M21 6l-2 2"/>')+'</span>'));
fs.mkdirSync(app,{recursive:true});
fs.cpSync(site,path.join(app,'bundled'),{recursive:true});
fs.copyFileSync(path.join(root,'web-app-icon-192.png'),path.join(app,'icon.png'));
fs.writeFileSync(path.join(app,'appinfo.json'),JSON.stringify({id:'com.zikra.tv',version,vendor:'Zikra',type:'web',main:'index.html',title:'ذكرى',icon:'icon.png',largeIcon:'icon.png',requiredACG:[],disableBackHistoryAPI:true},null,2));
fs.writeFileSync(path.join(app,'config.js'),'window.ZIKRA_CONFIG='+JSON.stringify({hosted,version,build:number})+';');
fs.copyFileSync(path.join(__dirname,'shell.html'),path.join(app,'index.html'));
console.log(JSON.stringify({app,site,version}));
