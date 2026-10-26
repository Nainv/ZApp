const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),cp=require('node:child_process'),vm=require('node:vm');
const root=path.resolve(__dirname,'..');
test('LG build bundles the real app, preserves Android sources, and never offers an APK',()=>{
 const temp=fs.mkdtempSync(path.join(os.tmpdir(),'zikra-lg-')),out=path.join(temp,'build');
 try{
  const before=fs.readFileSync(path.join(root,'index.html'),'utf8');
  cp.execFileSync(process.execPath,[path.join(root,'webos/build.cjs'),out],{env:{...process.env,GITHUB_RUN_NUMBER:'4321'}});
  assert.equal(fs.readFileSync(path.join(root,'index.html'),'utf8'),before);
  const metadata=JSON.parse(fs.readFileSync(path.join(out,'app/appinfo.json')));
  assert.equal(metadata.version,'1.4.321');assert.equal(metadata.id,'com.zikra.tv');
  for(const file of ['cells/settings/index.html','cells/mushaf/index.html','assets/data/quran-text.js','assets/audio/brand-dua.mp3'])assert(fs.statSync(path.join(out,'app/bundled',file)).size>0);
  const home=fs.readFileSync(path.join(out,'site/index.html'),'utf8');
  assert(home.includes('webos-bridge.js?v=4321'));assert(home.includes('type="text/zikra-script"'));
  assert(!home.includes('?v=231'));assert(home.includes('p-countdown'));assert(!home.includes('p-icon">☾'));
  const updater=fs.readFileSync(path.join(out,'site/assets/js/app-updater.js'),'utf8');assert(!updater.includes('.apk'));assert(updater.includes('ZikraWebOS.refresh'));
  const settings=fs.readFileSync(path.join(out,'site/assets/js/settings-hub.js'),'utf8');assert(settings.includes("button('اختيار المدينة — LG',chooseCity"));
  const remote=fs.readFileSync(path.join(out,'site/assets/js/tv-remote.js'),'utf8');assert(remote.includes('event.keyCode===461'));
  const shell=fs.readFileSync(path.join(out,'app/index.html'),'utf8');assert(shell.includes("frame.style.width='1280px'"));assert(shell.includes("event.source!==frame.contentWindow"));assert(shell.includes("open('bundled')"));
  for(const file of ['bridge.js','build.cjs'])new vm.Script(fs.readFileSync(path.join(root,'webos',file),'utf8'));
  assert.throws(()=>cp.execFileSync(process.execPath,[path.join(root,'webos/build.cjs'),out],{stdio:'ignore'}));
 }finally{fs.rmSync(temp,{recursive:true,force:true});}
});
test('LG bridge restores only app-owned storage before running shared scripts',async()=>{
 const listeners={},values={unrelated:'keep',zikra_city:'old',quran_bookmark:'old'},sent=[];
 class Storage{setItem(k,v){values[k]=String(v)}removeItem(k){delete values[k]}clear(){for(const k of Object.keys(values))delete values[k]}}
 const storage=new Storage();
 const ls=new Proxy(storage,{ownKeys:()=>Object.keys(values),getOwnPropertyDescriptor:()=>({enumerable:true,configurable:true}),get:(t,k)=>k==='getItem'?key=>values[key]??null:k in t?t[k]:values[k]});
 const parent={postMessage(message){sent.push(message);if(message.command==='state-request')queueMicrotask(()=>listeners.message.forEach(fn=>fn({source:parent,data:{type:'zikra-lg-state',values:{zikra_city:'Erfurt',unrelated:'erase?',quran_bookmark:'new'}}})));}};
 const document={documentElement:{classList:{add(){}}},readyState:'complete',querySelectorAll:()=>[],dispatchEvent(){},getElementById:()=>null,addEventListener(){}};
 const context={document,parent,window:null,localStorage:ls,Storage,location:{search:'?lg-shell=1',origin:'https://example.test'},URLSearchParams,Event,console,setTimeout,clearTimeout,queueMicrotask};
 context.window=context;context.addEventListener=(type,fn)=>(listeners[type]??=[]).push(fn);context.removeEventListener=(type,fn)=>listeners[type]=listeners[type].filter(x=>x!==fn);
 vm.runInNewContext(fs.readFileSync(path.join(root,'webos/bridge.js'),'utf8'),context);
 await new Promise(resolve=>setImmediate(resolve));
 assert.equal(values.unrelated,'keep');assert.equal(values.zikra_city,'Erfurt');assert.equal(values.quran_bookmark,'new');
 const state=sent.find(x=>x.command==='storage');assert(state);assert(!('unrelated'in state.values));assert(sent.some(x=>x.command==='ready'));
});
