const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const window={fetch:async path=>new Response(fs.readFileSync('site/'+path.replace(/^\.\//,'').split('?')[0]))};
const sandbox={window,fetch:window.fetch,Response,Blob,TextEncoder,URLSearchParams,Uint32Array,Uint8Array,DataView,Set,Map,JSON,Date,setInterval:()=>0};
vm.createContext(sandbox);vm.runInContext(fs.readFileSync('browser/zip.js','utf8'),sandbox);vm.runInContext(fs.readFileSync('browser/engine.js','utf8'),sandbox);
(async()=>{
 const c=JSON.parse(fs.readFileSync('site/catalog.json'));fs.mkdirSync('build/browser-zips',{recursive:true});
 for(const p of c.presets){const q=new URLSearchParams({preset:p.id});const preview=await window.initializrFetch('/preview',{body:q});assert.equal(preview.status,200);const pom=await preview.text();assert(pom.includes('<version>'+c.boot+'</version>'));assert(pom.includes('<java.version>'+c.java+'</java.version>'));const r=await window.initializrFetch('/generate',{body:q});assert.equal(r.status,200);fs.writeFileSync('build/browser-zips/'+p.id+'.zip',Buffer.from(await r.arrayBuffer()));}
 for(const fields of [{preset:'custom',deps:'gateway,web'},{packageName:'com.class.x'},{serverPort:'9001'},{customDeps:'org.postgresql:postgresql'}])assert.equal((await window.initializrFetch('/generate',{body:new URLSearchParams(fields)})).status,400);
 console.log('PASS: six browser ZIPs and invalid inputs');
})().catch(e=>{console.error(e);process.exitCode=1;});
