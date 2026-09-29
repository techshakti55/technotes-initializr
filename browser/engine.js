'use strict';
// Static-host adapter. Inputs remain in browser memory; only fixed templates are fetched.
(()=>{
const nativeFetch=window.fetch.bind(window), resources={};
async function asset(path){if(!resources[path])resources[path]=nativeFetch('./'+path,{cache:'no-cache'}).then(r=>{if(!r.ok)throw Error('Could not load '+path);return r.text();});return resources[path];}
const xml=s=>s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
const keywords=new Set('abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while true false null _'.split(' '));
const validName=s=>s.split('.').every(p=>/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(p)&&!keywords.has(p));
const block=(s,tag)=>{const m=s.match(new RegExp('<'+tag+'>([\\s\\S]*?)</'+tag+'>'));return m?m[1]:'';};
const depsFrom=s=>[...block(s,'dependencies').matchAll(/<dependency>[\s\S]*?<\/dependency>/g)].map(m=>m[0]);
const ga=s=>block(s,'groupId').trim()+':'+block(s,'artifactId').trim();
function validate(q,c){
 const p=c.presets.find(p=>p.id===(q.get('preset')||'oauth'));if(!p)throw Error('Unknown preset');
 const group=(q.get('groupId')||'com.technotes').trim(), artifact=(q.get('artifactId')||p.artifact).trim(), pkg=(q.get('packageName')||p.pkg).trim();
 const name=(q.get('name')||artifact).trim(), port=Number(q.get('serverPort')||p.port), java=q.get('javaVersion')||c.java;
 if(!validName(group)||group.length>100)throw Error('Invalid group ID');if(!validName(pkg)||pkg.length>160)throw Error('Invalid Java package');
 if(!/^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/.test(artifact)||artifact.length>80)throw Error('Invalid artifact ID');
 if(!name||name.length>120||/[\x00-\x1f]/.test(name))throw Error('Invalid project name');
 if(!Number.isInteger(port)||port<1||port>65535)throw Error('Port must be 1-65535');if(p.id==='oauth'&&port!==9000)throw Error('The fixed OAuth contract requires port 9000');
 if(java!==c.java)throw Error('This baseline requires Java '+c.java);
 const d=new Set((q.has('deps')?q.get('deps'):p.deps).split(',').map(s=>s.trim()).filter(Boolean)), known=new Set(c.dependencies.map(x=>x.id));
 for(const id of d)if(!known.has(id))throw Error('Unknown dependency '+id);
 for(const id of p.deps.split(',').filter(Boolean))if(!d.has(id))throw Error('Preset requires '+id);
 const has=(...ids)=>ids.some(id=>d.has(id));
 if(has('gateway','webflux')&&has('web','eureka-server','config-server','oauth2-authorization-server','admin-server'))throw Error('Reactive WebFlux/Gateway cannot be combined with servlet dependencies');
 if(has('eureka-client')&&has('eureka-server'))throw Error('Choose Eureka Server or Client');
 if(has('config-client')&&has('config-server'))throw Error('Choose Config Server or Client');
 if(has('postgresql')&&has('mysql'))throw Error('Choose one SQL driver');
 if(has('jpa','flyway')&&!has('postgresql','mysql'))throw Error('JPA/Flyway requires a SQL driver');
 if(has('flyway')&&!has('jpa'))throw Error('This Flyway baseline requires JPA');
 return {p,group,artifact,pkg,name,port,java,d,custom:q.get('customDeps')||''};
}
function pom(v,modules){
 const {group,artifact,name,java,d,custom}=v,map=new Map();
 const order=['web','webflux','actuator','validation','security','oauth2-resource-server','mail','thymeleaf','cache','lombok','jpa','mongodb','redis','kafka','mysql','postgresql','eureka-client','eureka-server','config-client','config-server','gateway','openfeign','admin-client','admin-server','tracing','zipkin','devtools','oauth2-authorization-server','flyway'];
 const selected=[...order.filter(k=>d.has(k)),'base'];if(d.has('flyway'))selected.push(d.has('mysql')?'flyway-mysql':'flyway-postgresql');
 for(const id of selected)for(const dep of depsFrom(modules[id]))map.set(ga(dep),dep);
 for(const key of ['org.springframework.security:spring-security-test','org.springframework.boot:spring-boot-starter-test'])if(map.has(key)){const value=map.get(key);map.delete(key);map.set(key,value);}
 const lines=custom.split(/\r?\n/).map(s=>s.trim()).filter(s=>s&&!s.startsWith('#'));
 for(const line of lines){const a=line.split(':');if(a.length<2||a.length>4||!a.slice(0,2).every(s=>/^[A-Za-z0-9_.-]+$/.test(s))||(a[2]&&!/^[A-Za-z0-9_.+-]+$/.test(a[2]))||(a[3]&&!['compile','provided','runtime','test'].includes(a[3])))throw Error('Invalid Maven coordinates: '+line);
  if(a[0].startsWith('org.springframework')||['org.flywaydb','de.codecentric'].includes(a[0]))throw Error('Use built-in Spring/Flyway/Admin dependencies');
  const key=a[0]+':'+a[1];if(map.has(key))throw Error('Duplicate dependency: '+key);
  map.set(key,'<dependency><groupId>'+a[0]+'</groupId><artifactId>'+a[1]+'</artifactId>'+(a[2]?'<version>'+a[2]+'</version>':'')+(a[3]?'<scope>'+a[3]+'</scope>':'')+'</dependency>');
 }
 let props='\n        <java.version>'+java+'</java.version>\n',bom='';
 for(const [key,tag] of [['eureka-client','spring-cloud.version'],['admin-client','spring-boot-admin.version']]){
  const enabled=key==='eureka-client'?[...d].some(x=>['eureka-client','eureka-server','config-client','config-server','gateway','openfeign'].includes(x)):[...d].some(x=>['admin-client','admin-server'].includes(x));
  if(enabled){props+='        <'+tag+'>'+block(modules[key],tag)+'</'+tag+'>\n';bom+=block(modules[key],'dependencyManagement');}
 }
 // Each BOM block contains one dependencies element; combine its entries.
 const boms=[...bom.matchAll(/<dependency>[\s\S]*?<\/dependency>/g)].map(m=>m[0]).join('\n');
 let s=modules.base.replace('<groupId>com.technotes</groupId>','<groupId>'+group+'</groupId>').replace('<artifactId>placeholder</artifactId>','<artifactId>'+artifact+'</artifactId>').replace('<name>placeholder</name>','<name>'+xml(name)+'</name>');
 s=s.replace(/<properties>[\s\S]*?<\/properties>/,'<properties>'+props+'    </properties>').replace(/<dependencies>[\s\S]*?<\/dependencies>/,'<dependencies>\n'+[...map.values()].join('\n')+'\n    </dependencies>');
 if(boms)s=s.replace('    <build>','    <dependencyManagement><dependencies>'+boms+'</dependencies></dependencyManagement>\n    <build>');
 return s;
}
function yaml(v){const {d,artifact,port}=v;let y='spring:\n  application:\n    name: '+artifact+'\n';
 if(d.has('jpa')||d.has('flyway'))y+='  datasource:\n    url: ${DB_URL:jdbc:'+(d.has('mysql')?'mysql://localhost:3306/':'postgresql://localhost:5432/')+artifact.replace(/-/g,'_')+'}\n    username: ${DB_USERNAME}\n    password: ${DB_PASSWORD}\n  jpa:\n    open-in-view: false\n    hibernate:\n      ddl-auto: validate\n';
 if(d.has('mongodb'))y+='  data:\n    mongodb:\n      uri: ${MONGODB_URI:mongodb://localhost:27017/technotes_notes}\n';
 if(d.has('config-server'))y+='  profiles:\n    active: native\n  cloud:\n    config:\n      server:\n        native:\n          search-locations: ${CONFIG_LOCATIONS:file:./config-repo/}\n';
 if(d.has('config-client'))y+='  config:\n    import: optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}\n';
 if(d.has('admin-client'))y+='  boot:\n    admin:\n      client:\n        url: ${ADMIN_URL:http://localhost:1111}\n';
 y+='\nserver:\n  port: '+port+'\n';
 if(d.has('eureka-server'))y+='\neureka:\n  client:\n    register-with-eureka: false\n    fetch-registry: false\n';
 else if(d.has('eureka-client'))y+='\neureka:\n  client:\n    service-url:\n      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}\n';
 if(['actuator','admin-client','tracing','zipkin'].some(x=>d.has(x))){y+='\nmanagement:\n  endpoints:\n    web:\n      exposure:\n        include: health,info\n  endpoint:\n    health:\n      show-details: never\n';if(d.has('tracing')||d.has('zipkin'))y+='  tracing:\n    sampling:\n      probability: 0.1\n';if(d.has('zipkin'))y+='  zipkin:\n    tracing:\n      endpoint: ${ZIPKIN_URL:http://localhost:9411/api/v2/spans}\n';}return y;
}
async function files(v,c,xmlPom){const {artifact,pkg,d,p}=v,cls=artifact.split('-').map(s=>s[0].toUpperCase()+s.slice(1)).join('')+'Application';
 let imports='import org.springframework.boot.SpringApplication;\nimport org.springframework.boot.autoconfigure.SpringBootApplication;\n',anns='@SpringBootApplication\n';
 for(const [id,path] of [['eureka-server','org.springframework.cloud.netflix.eureka.server.EnableEurekaServer'],['config-server','org.springframework.cloud.config.server.EnableConfigServer'],['admin-server','de.codecentric.boot.admin.server.config.EnableAdminServer'],['openfeign','org.springframework.cloud.openfeign.EnableFeignClients']])if(d.has(id)){imports+='import '+path+';\n';anns+='@'+path.split('.').pop()+'\n';}
 const fs={},add=(path,text)=>fs[artifact+'/'+path]=text;
 add('pom.xml',xmlPom);add('src/main/resources/application.yml',yaml(v));
 add('src/main/java/'+pkg.replace(/\./g,'/')+'/'+cls+'.java','package '+pkg+';\n\n'+imports+'\n'+anns+'public class '+cls+' {\n    public static void main(String[] args) {\n        SpringApplication.run('+cls+'.class, args);\n    }\n}\n');
 for(const dir of ['config','controller','service','repository','dto',d.has('mongodb')?'document':'entity'])add('src/main/java/'+pkg.replace(/\./g,'/')+'/'+dir+'/package-info.java','/** '+dir+' layer. */\npackage '+pkg+'.'+dir+';\n');
 add('.gitignore','target/\n.idea/\n*.iml\n.env\n.env.*\nsecrets/\n*.key\n*.jks\n*.p12\n*.log\n');
 add('.editorconfig','root = true\n[*]\ncharset = utf-8\nend_of_line = lf\ninsert_final_newline = true\nindent_style = space\nindent_size = 4\n[*.cmd]\nend_of_line = crlf\n');
 for(const name of ['mvnw','mvnw.cmd','maven-wrapper.properties'])add(name==='maven-wrapper.properties'?'.mvn/wrapper/'+name:name,await asset('templates/'+name));
 if(d.has('flyway'))add('src/main/resources/db/migration/README.md','Add reviewed V1__description.sql migrations here. No credentials generated.\n');
 if(d.has('config-server'))add('config-repo/application.yml','# Shared local configuration. Never commit passwords.\n');
 if(p.id==='oauth')add('docs/FIXED-OAUTH-CONTRACT.md',await asset('templates/OAUTH-CONTRACT.md'));
 add('technotes-baseline.properties','generator=4.1.0-browser\nboot='+c.boot+'\ncloud='+c.cloud+'\njava='+c.java+'\npreset='+p.id+'\n');
 add('README.md','# '+artifact+'\n\nGenerated privately in the browser by TechNotes Initializr. This is a scaffold, not a finished service.\n\nJava '+c.java+'; Boot '+c.boot+'; Cloud '+c.cloud+' when selected.\n\n1. Extract to a new folder and open pom.xml in IntelliJ. Choose JDK 21.\n2. Reload Maven; dependency downloads require internet.\n3. For SQL: install the selected database locally, create the database in application.yml, and set DB_USERNAME/DB_PASSWORD in Run Configuration. DB_URL is optional. Add Flyway migrations before JPA entities.\n4. For MongoDB: start local MongoDB, optionally set MONGODB_URI.\n5. Build on Windows with `.\\mvnw.cmd clean package`; Linux: `chmod +x mvnw && ./mvnw clean package`.\n6. Run the Application main class after local prerequisites are ready.\n\nNo business tests are generated. Implement and test your APIs. OAuth requires PKCE configuration, persistent signing keys/authorization state, private owner provisioning and /users/me; see the fixed contract for the OAuth preset. No signup API is generated. Gateway routes require configuration.\n\nUpdate the generator source through GitHub; existing generated services do not update automatically.\n');return fs;}
window.initializrFetch=async(url,opts={})=>{try{
 if(url==='/api/catalog')return new Response(await asset('catalog.json'),{headers:{'Content-Type':'application/json'}});
 if(!['/generate','/preview'].includes(url))throw Error('Unsupported local operation');
 const c=JSON.parse(await asset('catalog.json')),modules=JSON.parse(await asset('modules.json')),v=validate(new URLSearchParams(opts.body),c),text=pom(v,modules);
 if(url==='/preview')return new Response(text,{headers:{'Content-Type':'application/xml'}});
 return new Response(window.makeProjectZip(await files(v,c,text)),{headers:{'Content-Type':'application/zip'}});
 }catch(e){return new Response(e.message,{status:400});}};
// Exposed for parity checks, not a remote API.
window.TechNotesEngine={validate,pom,yaml,files};
})();
// Detect a new successful deployment in an already-open tab; never discard form inputs automatically.
(async()=>{
 try{
  const getBuild=()=>fetch('./build.json?t='+Date.now(),{cache:'no-store'}).then(r=>r.json());
  const first=await getBuild();
  setInterval(async()=>{try{const next=await getBuild();if(next.revision===first.revision||document.getElementById('updateAvailable'))return;
    const button=document.createElement('button');button.id='updateAvailable';button.className='secondary';button.type='button';button.textContent='New version available — reload';button.onclick=()=>location.reload();document.querySelector('header').append(button);
  }catch{}},60000);
 }catch{}
})();
