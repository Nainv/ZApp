const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const platform=fs.readFileSync('assets/js/tv-platform.js','utf8');
const motion=fs.readFileSync('assets/js/tv-motion.js','utf8');
function setup(tv,reduced=false){
 const documentEvents={},windowEvents={},classes=new Set(),animations=[];
 const classList={add:x=>classes.add(x),contains:x=>classes.has(x)};
 const body={dataset:{},animate:(frames,options)=>{animations.push({frames,options});return {cancel(){}}}};
 const document={documentElement:{classList,dataset:{}},body,readyState:'complete',activeElement:null,querySelector:()=>null,querySelectorAll:()=>[],addEventListener:(name,fn)=>documentEvents[name]=fn};
 const window={AndroidTV:{isTelevision:()=>tv},matchMedia:()=>({matches:reduced}),addEventListener:(name,fn)=>windowEvents[name]=fn};
 const context=vm.createContext({window,document,parent:window,MutationObserver:class{observe(){}},WeakMap});
 vm.runInContext(platform,context);vm.runInContext(motion,context);
 function control(){const values=new Set();return {classList:{add:x=>values.add(x),remove:x=>values.delete(x),toggle:(x,on)=>on?values.add(x):values.delete(x)},closest(){return this},animate:body.animate,values};}
 return {documentEvents,windowEvents,document,classes,animations,control};
}
test('Phone build keeps existing focus and motion ownership',()=>{
 const s=setup(false);assert.equal(s.classes.has('tv-device'),false);assert.equal(s.animations.length,0);assert.equal(s.documentEvents.focusin,undefined);
});
test('TV focus transfers immediately and one press flashes once',()=>{
 const s=setup(true),first=s.control(),second=s.control();
 s.documentEvents.focusin({target:first});s.documentEvents.focusin({target:second});
 assert.equal(first.values.has('tv-motion-focus'),false);assert.equal(second.values.has('tv-motion-focus'),true);
 s.documentEvents.click({target:second});assert.equal(s.animations.at(-1).options.duration,140);
 s.documentEvents.focusout({relatedTarget:null});assert.equal(second.values.has('tv-motion-focus'),false);
});
test('Reduced motion shortens entrance and activation',()=>{
 const s=setup(true,true);assert.equal(s.animations[0].options.duration,70);
 s.documentEvents.click({target:s.control()});assert.equal(s.animations.at(-1).options.duration,70);
});
