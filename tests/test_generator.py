"""HTTP contract tests; run against local generator. No third-party Python packages."""
import io, json, os, urllib.request, urllib.parse, urllib.error, zipfile
import xml.etree.ElementTree as ET
BASE = os.environ.get('INITIALIZR_URL','http://localhost:9090')
count = 0

def req(path, fields=None, expect=200, headers=None, raw=None):
    global count
    data = urllib.parse.urlencode(fields).encode() if fields is not None else raw
    h = {'Content-Type':'application/x-www-form-urlencoded'} if data is not None else {}
    h.update(headers or {})
    try:
        r=urllib.request.urlopen(urllib.request.Request(BASE+path,data=data,headers=h))
    except urllib.error.HTTPError as e: r=e
    body=r.read()
    assert r.status == expect, (path,r.status,body[:300],expect)
    count += 1
    return body, r.headers

catalog=json.loads(req('/api/catalog')[0]); assert catalog['java']=='21'
for p in catalog['presets']:
    form={'preset':p['id']}
    raw,headers=req('/generate',form)
    assert 'attachment' in headers['Content-Disposition']
    z=zipfile.ZipFile(io.BytesIO(raw)); base=p['artifact']+'/'
    pom=z.read(base+'pom.xml'); root=ET.fromstring(pom); ns={'m':'http://maven.apache.org/POM/4.0.0'}
    assert root.find('m:parent/m:version',ns).text == '3.5.16'
    assert root.find('m:properties/m:java.version',ns).text == '21'
    ga=[(d.find('m:groupId',ns).text,d.find('m:artifactId',ns).text) for d in root.findall('m:dependencies/m:dependency',ns)]
    assert len(ga)==len(set(ga))
    for name in ['mvnw','mvnw.cmd','.mvn/wrapper/maven-wrapper.properties','README.md','technotes-baseline.properties']:assert base+name in z.namelist()
    assert b'include: "*"' not in z.read(base+'src/main/resources/application.yml')
    assert req('/preview',form)[0] == pom
    if p['id']=='oauth':
        assert ('org.flywaydb','flyway-database-postgresql') in ga
        assert ('org.springframework.boot','spring-boot-starter-oauth2-authorization-server') in ga
        assert base+'docs/FIXED-OAUTH-CONTRACT.md' in z.namelist()
        assert not root.findall('m:dependencyManagement',ns)
    if p['id']=='gateway':assert ('org.springframework.boot','spring-boot-starter-web') not in ga
    print('PASS preset:',p['id'])

for fields in [
    {'preset':'invalid'}, {'packageName':'com.class.auth'}, {'groupId':'com..notes'},
    {'artifactId':'../escape'}, {'artifactId':'9bad'}, {'serverPort':'99999'}, {'serverPort':'9001'},
    {'javaVersion':'17'}, {'deps':'web'}, {'preset':'custom','deps':'bogus'},
    {'preset':'custom','deps':'gateway,web'}, {'preset':'custom','deps':'webflux,oauth2-authorization-server'},
    {'preset':'custom','deps':'jpa'}, {'preset':'custom','deps':'postgresql,mysql'},
    {'preset':'custom','deps':'eureka-server,eureka-client'},
    {'customDeps':'a:b:1:system'}, {'customDeps':'a:b:1\na:b:2'},
    {'customDeps':'org.springframework.boot:spring-boot-starter-web:4.0.0'},
    {'customDeps':'org.postgresql:postgresql'}, {'customDeps':'a:b:<script>'},
]:req('/generate',fields,expect=400)
req('/generate',{'preset':'custom','customDeps':'com.example:example:1.0:test'})
req('/generate',{'preset':'custom','deps':'jpa,mysql,flyway'})
req('/generate',{'preset':'custom','name':'Demo & <name>'})
req('/generate',{},expect=403,headers={'Origin':'https://evil.example'})
req('/generate',{},expect=415,headers={'Content-Type':'application/json'})
req('/generate',raw=b'x'*32769,expect=413)
req('/generate',raw=b'preset=custom&preset=oauth',expect=400)
req('/generate',expect=405)
req('/save-custom-dep',{},expect=405)
req('/unknown',expect=404)
req('/health')
_,headers=req('/')
assert "script-src 'self'" in headers['Content-Security-Policy']
req('/app.js');req('/style.css')
print(f'PASS: {count} HTTP checks, 6 ZIP/POM presets; no service integration claim.')
