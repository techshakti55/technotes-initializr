import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import javax.lang.model.SourceVersion;

/** TechNotes generator. No external runtime dependencies, shell execution or remote fetching. */
public class LocalInitializr {
    private static final Properties VERSIONS = versions();
    private static final String BOOT = VERSIONS.getProperty("boot");
    private static final String CLOUD = VERSIONS.getProperty("cloud");
    private static final String ADMIN = VERSIONS.getProperty("admin");
    private static final String JAVA = VERSIONS.getProperty("java");
    private static final String[][] CATALOG = {
        {"web","Spring Web (MVC)"}, {"webflux","Spring WebFlux"}, {"security","Spring Security"},
        {"oauth2-authorization-server","OAuth2 Authorization Server"}, {"oauth2-resource-server","OAuth2 Resource Server"},
        {"jpa","Spring Data JPA"}, {"postgresql","PostgreSQL Driver"}, {"mysql","MySQL Driver"}, {"flyway","Flyway Migration"},
        {"mongodb","MongoDB"}, {"validation","Validation"}, {"lombok","Lombok"},
        {"eureka-server","Eureka Server"}, {"eureka-client","Eureka Client"}, {"config-server","Config Server"},
        {"config-client","Config Client"}, {"gateway","Reactive Gateway"}, {"openfeign","OpenFeign"},
        {"actuator","Actuator"}, {"devtools","DevTools"}, {"redis","Redis"}, {"kafka","Kafka"},
        {"mail","Mail"}, {"thymeleaf","Thymeleaf"}, {"cache","Cache"},
        {"admin-client","Boot Admin Client"}, {"admin-server","Boot Admin Server"},
        {"tracing","Micrometer Tracing"}, {"zipkin","Zipkin Reporter"}
    };
    record Preset(String id, String label, String artifact, String pkg, int port, String deps, String note) {}
    private static final List<Preset> PRESETS = List.of(
        new Preset("oauth", "User / OAuth", "technotes-user-oauth-service", "com.technotes.auth", 9000,
            "web,security,oauth2-authorization-server,oauth2-resource-server,jpa,postgresql,flyway,validation,lombok",
            "Standalone PostgreSQL baseline. Fixed OAuth contract included; PKCE, persistent keys, owner provisioning and /users/me still require implementation."),
        new Preset("notes", "Notes Service", "technotes-notes-service", "com.technotes.notes", 8081,
            "web,mongodb,validation,lombok", "Local MongoDB baseline. Add business APIs and JWT validation during implementation."),
        new Preset("eureka", "Eureka Server", "technotes-eureka-server", "com.technotes.eureka", 8761,
            "web,eureka-server", "Standalone discovery server with self-registration disabled."),
        new Preset("config", "Config Server", "technotes-config-server", "com.technotes.config", 8888,
            "web,config-server", "Local native configuration backend. Config files belong in the config-repo folder."),
        new Preset("gateway", "API Gateway", "technotes-api-gateway", "com.technotes.gateway", 8080,
            "gateway", "Reactive gateway baseline. Configure routes when services are ready."),
        new Preset("custom", "Custom service", "technotes-custom-service", "com.technotes.custom", 8082,
            "", "Choose your own dependencies. The TechNotes Java and Spring version baseline stays fixed.")
    );
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args.length > 0 ? args[0] : System.getenv().getOrDefault("PORT", "9090"));
        String host = System.getenv().getOrDefault("BIND_ADDRESS", "127.0.0.1");
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 64);
        server.createContext("/", LocalInitializr::handle);
        server.setExecutor(new ThreadPoolExecutor(4, 8, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), new ThreadPoolExecutor.CallerRunsPolicy()));
        server.start();
        System.out.println("TechNotes Initializr 4: http://" + host + ":" + port);
        System.out.println("Java " + JAVA + " | Boot " + BOOT + " | Cloud " + CLOUD);
        System.out.println("Ctrl+C to stop. Read HOSTING.md before exposing this server.");
    }
    private static void handle(HttpExchange ex) throws IOException {
        try {
            ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            ex.getResponseHeaders().set("X-Frame-Options", "DENY");
            ex.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
            ex.getResponseHeaders().set("Cache-Control", "no-store");
            String path = ex.getRequestURI().getPath();
            if (path.equals("/generate") || path.equals("/preview")) {
                if (!ex.getRequestMethod().equals("POST")) { send(ex,405,"text/plain","Use POST"); return; }
                String origin = ex.getRequestHeaders().getFirst("Origin");
                if (origin != null) {
                    URI uri = URI.create(origin);
                    if (uri.getRawAuthority() == null || !uri.getRawAuthority().equalsIgnoreCase(ex.getRequestHeaders().getFirst("Host"))) {
                        send(ex,403,"text/plain","Cross-origin requests are not allowed"); return;
                    }
                }
                if (!Objects.toString(ex.getRequestHeaders().getFirst("Content-Type"), "").startsWith("application/x-www-form-urlencoded")) {
                    send(ex,415,"text/plain","Expected form data"); return;
                }
                byte[] bytes = ex.getRequestBody().readNBytes(32769);
                if (bytes.length > 32768) { send(ex,413,"text/plain","Request too large"); return; }
                generate(ex, parseQuery(new String(bytes,StandardCharsets.UTF_8)), path.equals("/preview")); return;
            }
            if (!ex.getRequestMethod().equals("GET")) { send(ex,405,"text/plain","Use GET"); return; }
            switch (path) {
                case "/" -> send(ex,200,"text/html; charset=UTF-8",resource("index.html"));
                case "/app.js" -> send(ex,200,"text/javascript; charset=UTF-8",resource("app.js"));
                case "/style.css" -> send(ex,200,"text/css; charset=UTF-8",resource("style.css"));
                case "/api/catalog" -> send(ex,200,"application/json",catalogJson());
                case "/health" -> send(ex,200,"application/json","{\"status\":\"UP\"}");
                default -> send(ex,404,"text/plain","Not found");
            }
        } catch (IllegalArgumentException e) { send(ex,400,"text/plain; charset=UTF-8",e.getMessage()); }
        catch (Exception e) { System.err.println(e); send(ex,500,"text/plain","Generation failed. Check server logs."); }
        finally { ex.close(); }
    }
    private static void generate(HttpExchange ex, Map<String,String> q, boolean preview) throws IOException {
        String presetId = q.getOrDefault("preset","oauth");
        Preset preset = PRESETS.stream().filter(p -> p.id.equals(presetId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown preset"));
        String groupId = q.getOrDefault("groupId","com.technotes").trim();
        String artifactId = q.getOrDefault("artifactId",preset.artifact).trim();
        String pkg = q.getOrDefault("packageName",preset.pkg).trim();
        if (!SourceVersion.isName(groupId) || groupId.length()>100) throw new IllegalArgumentException("Group must be a valid Java-style dotted name");
        if (!SourceVersion.isName(pkg) || pkg.length()>160) throw new IllegalArgumentException("Package must contain valid Java identifiers, not keywords");
        if (!artifactId.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*") || artifactId.length()>80) throw new IllegalArgumentException("Artifact must start with a lowercase letter and contain lowercase letters, numbers or single hyphens");
        String name = q.getOrDefault("name",artifactId).trim();
        if (name.isBlank() || name.length()>120 || name.chars().anyMatch(c -> c<32)) throw new IllegalArgumentException("Name must be 1-120 printable characters");
        if (!q.getOrDefault("javaVersion",JAVA).equals(JAVA)) throw new IllegalArgumentException("This baseline requires Java " + JAVA);
        int port;
        try { port = Integer.parseInt(q.getOrDefault("serverPort",Integer.toString(preset.port))); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid port"); }
        if (port<1 || port>65535) throw new IllegalArgumentException("Port must be 1-65535");
        if (preset.id.equals("oauth") && port !=9000) throw new IllegalArgumentException("The fixed local OAuth contract requires port 9000");
        Set<String> deps = new LinkedHashSet<>();
        for (String d : q.getOrDefault("deps",preset.deps).split(",")) if (!d.isBlank()) deps.add(d.trim());
        for (String d : preset.deps.split(",")) if (!d.isBlank() && !deps.contains(d)) throw new IllegalArgumentException("Preset requires " + d + ". Choose Custom service to change the baseline.");
        Set<String> allowed = new HashSet<>(); for (String[] d : CATALOG) allowed.add(d[0]);
        if (!allowed.containsAll(deps)) throw new IllegalArgumentException("Unknown dependency selection");
        String custom = q.getOrDefault("customDeps", "").trim();
        Set<String> customGA = new HashSet<>();
        for (String line : custom.split("\\R")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            if (!validCoords(line)) throw new IllegalArgumentException("Invalid Maven coordinates: " + line);
            String[] a=line.split(":",-1); String ga=a[0]+":"+a[1];
            if (!customGA.add(ga)) throw new IllegalArgumentException("Duplicate custom dependency: " + ga);
            if (a[0].startsWith("org.springframework") || a[0].equals("org.flywaydb") || a[0].equals("de.codecentric"))
                throw new IllegalArgumentException("Use the built-in catalog for Spring, Flyway and Boot Admin dependencies");
        }
        validateDeps(deps);
        String xml = pom(groupId, artifactId, name, JAVA, deps, custom);
        if (preview) { send(ex,200,"application/xml; charset=UTF-8",xml); return; }
        String cls = toClassName(artifactId)+"Application"; String base=artifactId+"/";
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(out,StandardCharsets.UTF_8)) {
            put(z,base+"pom.xml",xml);
            put(z,base+"src/main/java/"+pkg.replace('.','/')+"/"+cls+".java",mainClass(pkg,cls,deps));
            put(z,base+"src/main/resources/application.yml",yaml(artifactId,port,deps));
            put(z,base+"README.md",readme(artifactId,JAVA,deps));
            put(z,base+".gitignore","target/\n.idea/\n*.iml\n.env\n.env.*\n!.env.example\nsecrets/\n*.p12\n*.jks\n*.key\n*.log\n");
            put(z,base+".editorconfig","root = true\n[*]\ncharset = utf-8\nend_of_line = lf\ninsert_final_newline = true\nindent_style = space\nindent_size = 4\n[*.cmd]\nend_of_line = crlf\n");
            put(z,base+"mvnw",resource("wrapper/mvnw"));
            put(z,base+"mvnw.cmd",resource("wrapper/mvnw.cmd"));
            put(z,base+".mvn/wrapper/maven-wrapper.properties",resource("wrapper/maven-wrapper.properties"));
            put(z,base+"technotes-baseline.properties","generator=4.0.0\nboot="+BOOT+"\ncloud="+CLOUD+"\njava="+JAVA+"\npreset="+preset.id+"\n");
            if (deps.contains("flyway")) put(z,base+"src/main/resources/db/migration/README.md","Add reviewed V1__description.sql migrations here. No users or credentials are generated.\n");
            if (deps.contains("config-server")) put(z,base+"config-repo/application.yml","# Shared local configuration goes here. Never commit passwords.\n");
            if (preset.id.equals("oauth")) put(z,base+"docs/FIXED-OAUTH-CONTRACT.md",resource("OAUTH-CONTRACT.md"));
            for(String folder : List.of("config","controller","service","repository","dto",deps.contains("mongodb")?"document":"entity"))
                put(z,base+"src/main/java/"+pkg.replace('.','/')+"/"+folder+"/package-info.java","/** " + folder + " layer. Implement service behavior here. */\npackage " + pkg + "." + folder + ";\n");
        }
        ex.getResponseHeaders().set("Content-Type","application/zip");
        ex.getResponseHeaders().set("Content-Disposition","attachment; filename=\""+artifactId+".zip\"");
        ex.sendResponseHeaders(200,out.size()); ex.getResponseBody().write(out.toByteArray());
    }
    private static void validateDeps(Set<String> d) {
        boolean reactive=d.contains("gateway") || d.contains("webflux");
        if (reactive && (d.contains("web") || d.contains("eureka-server") || d.contains("config-server") || d.contains("oauth2-authorization-server") || d.contains("admin-server"))) throw new IllegalArgumentException("Reactive WebFlux/Gateway cannot be combined with these servlet server dependencies");
        if (d.contains("eureka-client") && d.contains("eureka-server")) throw new IllegalArgumentException("Choose Eureka Server or Client, not both");
        if (d.contains("config-client") && d.contains("config-server")) throw new IllegalArgumentException("Choose Config Server or Client, not both");
        if (d.contains("postgresql") && d.contains("mysql")) throw new IllegalArgumentException("Select one SQL database driver");
        if ((d.contains("jpa") || d.contains("flyway")) && !(d.contains("postgresql") || d.contains("mysql"))) throw new IllegalArgumentException("JPA/Flyway requires a SQL driver");
        if (d.contains("flyway") && !d.contains("jpa")) throw new IllegalArgumentException("This generator's Flyway baseline requires JPA for its datasource setup");
    }
    private static String pom(String groupId, String artifactId, String name, String javaVersion, Set<String> deps, String customDeps) {
        boolean cloud = deps.stream().anyMatch(Set.of("eureka-client","eureka-server","config-client","config-server","gateway","openfeign")::contains)
                || customDepsHasGroup(customDeps, "org.springframework.cloud");
        boolean admin = deps.contains("admin-client") || deps.contains("admin-server")
                || customDepsHasGroup(customDeps, "de.codecentric");
        StringBuilder d = new StringBuilder();
        addDep(d, deps, "web", "org.springframework.boot", "spring-boot-starter-web", null, false);
        addDep(d, deps, "webflux", "org.springframework.boot", "spring-boot-starter-webflux", null, false);
        addDep(d, deps, "actuator", "org.springframework.boot", "spring-boot-starter-actuator", null, false);
        addDep(d, deps, "validation", "org.springframework.boot", "spring-boot-starter-validation", null, false);
        addDep(d, deps, "security", "org.springframework.boot", "spring-boot-starter-security", null, false);
        addDep(d, deps, "oauth2-resource-server", "org.springframework.boot", "spring-boot-starter-oauth2-resource-server", null, false);
        addDep(d, deps, "mail", "org.springframework.boot", "spring-boot-starter-mail", null, false);
        addDep(d, deps, "thymeleaf", "org.springframework.boot", "spring-boot-starter-thymeleaf", null, false);
        addDep(d, deps, "cache", "org.springframework.boot", "spring-boot-starter-cache", null, false);
        addDep(d, deps, "lombok", "org.projectlombok", "lombok", null, true);
        addDep(d, deps, "jpa", "org.springframework.boot", "spring-boot-starter-data-jpa", null, false);
        addDep(d, deps, "mongodb", "org.springframework.boot", "spring-boot-starter-data-mongodb", null, false);
        addDep(d, deps, "redis", "org.springframework.boot", "spring-boot-starter-data-redis", null, false);
        addDep(d, deps, "kafka", "org.springframework.kafka", "spring-kafka", null, false);
        addDep(d, deps, "mysql", "com.mysql", "mysql-connector-j", "runtime", false);
        addDep(d, deps, "postgresql", "org.postgresql", "postgresql", "runtime", false);
        addDep(d, deps, "eureka-client", "org.springframework.cloud", "spring-cloud-starter-netflix-eureka-client", null, false);
        addDep(d, deps, "eureka-server", "org.springframework.cloud", "spring-cloud-starter-netflix-eureka-server", null, false);
        addDep(d, deps, "config-client", "org.springframework.cloud", "spring-cloud-starter-config", null, false);
        addDep(d, deps, "config-server", "org.springframework.cloud", "spring-cloud-config-server", null, false);
        addDep(d, deps, "gateway", "org.springframework.cloud", "spring-cloud-starter-gateway-server-webflux", null, false);
        addDep(d, deps, "openfeign", "org.springframework.cloud", "spring-cloud-starter-openfeign", null, false);
        addDep(d, deps, "admin-client", "de.codecentric", "spring-boot-admin-starter-client", null, false);
        addDep(d, deps, "admin-server", "de.codecentric", "spring-boot-admin-starter-server", null, false);
        addDep(d, deps, "tracing", "io.micrometer", "micrometer-tracing-bridge-brave", null, false);
        addDep(d, deps, "zipkin", "io.zipkin.reporter2", "zipkin-reporter-brave", null, false);
        if (deps.contains("devtools")) {
            d.append("        <dependency>\n            <groupId>org.springframework.boot</groupId>\n            <artifactId>spring-boot-devtools</artifactId>\n            <scope>runtime</scope>\n            <optional>true</optional>\n        </dependency>\n");
        }
        addDep(d, deps, "oauth2-authorization-server", "org.springframework.boot", "spring-boot-starter-oauth2-authorization-server", null, false);
        addDep(d, deps, "flyway", "org.flywaydb", "flyway-core", null, false);
        if (deps.contains("flyway") && deps.contains("postgresql"))
            d.append("<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>\n");
        if (deps.contains("flyway") && deps.contains("mysql"))
            d.append("<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-mysql</artifactId></dependency>\n");
        if (deps.contains("security") || deps.contains("oauth2-resource-server") || deps.contains("oauth2-authorization-server"))
            d.append("<dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>\n");
        appendCustomDeps(d, customDeps);
        d.append("        <dependency>\n            <groupId>org.springframework.boot</groupId>\n            <artifactId>spring-boot-starter-test</artifactId>\n            <scope>test</scope>\n        </dependency>\n");

        StringBuilder props = new StringBuilder("        <java.version>" + javaVersion + "</java.version>\n");
        if (cloud) props.append("        <spring-cloud.version>").append(CLOUD).append("</spring-cloud.version>\n");
        if (admin) props.append("        <spring-boot-admin.version>").append(ADMIN).append("</spring-boot-admin.version>\n");

        StringBuilder dm = new StringBuilder();
        if (cloud || admin) {
            dm.append("    <dependencyManagement>\n        <dependencies>\n");
            if (cloud) dm.append("            <dependency>\n                <groupId>org.springframework.cloud</groupId>\n                <artifactId>spring-cloud-dependencies</artifactId>\n                <version>${spring-cloud.version}</version>\n                <type>pom</type>\n                <scope>import</scope>\n            </dependency>\n");
            if (admin) dm.append("            <dependency>\n                <groupId>de.codecentric</groupId>\n                <artifactId>spring-boot-admin-dependencies</artifactId>\n                <version>${spring-boot-admin.version}</version>\n                <type>pom</type>\n                <scope>import</scope>\n            </dependency>\n");
            dm.append("        </dependencies>\n    </dependencyManagement>\n\n");
        }

        return """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>%s</version>
        <relativePath/>
    </parent>

    <groupId>%s</groupId>
    <artifactId>%s</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>%s</name>
    <description>Generated by TechNotes Initializr 4</description>

    <properties>
%s    </properties>

    <dependencies>
%s    </dependencies>

%s    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
""".formatted(BOOT, groupId, artifactId, xml(name), props, d, dm);
    }

    private static boolean customDepsHasGroup(String customDeps, String groupId) {
        if (customDeps == null || customDeps.isBlank()) return false;
        for (String line : customDeps.split("\\R")) {
            String[] p = line.trim().split(":", -1);
            if (p.length >= 2 && p[0].trim().equals(groupId)) return true;
        }
        return false;
    }

    private static void appendCustomDeps(StringBuilder b, String customDeps) {
        if (customDeps == null || customDeps.isBlank()) return;
        Set<String> seen = new LinkedHashSet<>();
        for (String line : customDeps.split("\\R")) {
            String x = line.trim();
            if (x.isBlank() || x.startsWith("#") || !seen.add(x)) continue;
            String[] p = x.split(":", -1);
            if (p.length < 2) continue;
            String group = p[0].trim();
            String artifact = p[1].trim();
            if (!group.matches("[A-Za-z0-9_.-]+") || !artifact.matches("[A-Za-z0-9_.-]+")) continue;
            String version = p.length > 2 ? p[2].trim() : "";
            String scope = p.length > 3 ? p[3].trim() : "";
            String marker = "<groupId>" + group + "</groupId>";
            if (b.toString().contains(marker + "\n            <artifactId>" + artifact + "</artifactId>") ||
                b.toString().contains(marker + "<artifactId>" + artifact + "</artifactId>"))
                throw new IllegalArgumentException("Duplicate dependency: " + group + ":" + artifact);
            b.append("        <dependency>\n            <groupId>").append(group).append("</groupId>\n            <artifactId>").append(artifact).append("</artifactId>\n");
            if (!version.isBlank()) b.append("            <version>").append(xml(version)).append("</version>\n");
            if (!scope.isBlank() && Set.of("compile","provided","runtime","test","system","import").contains(scope))
                b.append("            <scope>").append(scope).append("</scope>\n");
            b.append("        </dependency>\n");
        }
    }

    private static void addDep(StringBuilder b, Set<String> deps, String key, String group, String artifact, String scope, boolean optional) {
        if (!deps.contains(key)) return;
        b.append("        <dependency>\n            <groupId>").append(group).append("</groupId>\n            <artifactId>").append(artifact).append("</artifactId>\n");
        if (scope != null) b.append("            <scope>").append(scope).append("</scope>\n");
        if (optional) b.append("            <optional>true</optional>\n");
        b.append("        </dependency>\n");
    }

    private static String mainClass(String pkg, String cls, Set<String> deps) {
        StringBuilder imports = new StringBuilder("import org.springframework.boot.SpringApplication;\nimport org.springframework.boot.autoconfigure.SpringBootApplication;\n");
        StringBuilder anns = new StringBuilder("@SpringBootApplication\n");
        if (deps.contains("eureka-server")) { imports.append("import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;\n"); anns.append("@EnableEurekaServer\n"); }
        if (deps.contains("config-server")) { imports.append("import org.springframework.cloud.config.server.EnableConfigServer;\n"); anns.append("@EnableConfigServer\n"); }
        if (deps.contains("admin-server")) { imports.append("import de.codecentric.boot.admin.server.config.EnableAdminServer;\n"); anns.append("@EnableAdminServer\n"); }
        if (deps.contains("openfeign")) { imports.append("import org.springframework.cloud.openfeign.EnableFeignClients;\n"); anns.append("@EnableFeignClients\n"); }
        return "package " + pkg + ";\n\n" + imports + "\n" + anns + "public class " + cls + " {\n    public static void main(String[] args) {\n        SpringApplication.run(" + cls + ".class, args);\n    }\n}\n";
    }


    private static String yaml(String artifact, int port, Set<String> d) {
        StringBuilder y=new StringBuilder("spring:\n  application:\n    name: "+artifact+"\n");
        if(d.contains("jpa") || d.contains("flyway")) {
            String db=d.contains("mysql")?"mysql":"postgresql"; int dp=d.contains("mysql")?3306:5432;
            y.append("  datasource:\n    url: ${DB_URL:jdbc:"+db+"://localhost:"+dp+"/"+artifact.replace('-','_')+"}\n    username: ${DB_USERNAME}\n    password: ${DB_PASSWORD}\n  jpa:\n    open-in-view: false\n    hibernate:\n      ddl-auto: validate\n");
        }
        if(d.contains("mongodb")) y.append("  data:\n    mongodb:\n      uri: ${MONGODB_URI:mongodb://localhost:27017/technotes_notes}\n");
        if(d.contains("config-server")) y.append("  profiles:\n    active: native\n  cloud:\n    config:\n      server:\n        native:\n          search-locations: ${CONFIG_LOCATIONS:file:./config-repo/}\n");
        if(d.contains("config-client")) y.append("  config:\n    import: optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}\n");
        if(d.contains("admin-client")) y.append("  boot:\n    admin:\n      client:\n        url: ${ADMIN_URL:http://localhost:1111}\n");
        y.append("\nserver:\n  port: "+port+"\n");
        if(d.contains("eureka-server")) y.append("\neureka:\n  client:\n    register-with-eureka: false\n    fetch-registry: false\n");
        else if(d.contains("eureka-client")) y.append("\neureka:\n  client:\n    service-url:\n      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}\n");
        if(d.contains("actuator") || d.contains("admin-client") || d.contains("tracing") || d.contains("zipkin")) {
            y.append("\nmanagement:\n  endpoints:\n    web:\n      exposure:\n        include: health,info\n  endpoint:\n    health:\n      show-details: never\n");
            if(d.contains("tracing") || d.contains("zipkin")) y.append("  tracing:\n    sampling:\n      probability: 0.1\n");
            if(d.contains("zipkin")) y.append("  zipkin:\n    tracing:\n      endpoint: ${ZIPKIN_URL:http://localhost:9411/api/v2/spans}\n");
        }
        return y.toString();
    }
    private static String readme(String artifact,String java,Set<String> deps) {
        return "# "+artifact+"\n\nGenerated by TechNotes Initializr 4.0.0. This is a scaffold, not a completed service.\n\nJava "+java+" | Boot "+BOOT+" | Cloud "+CLOUD+" (only when selected).\n\nDependencies: "+String.join(", ",deps)+"\n\n## Start in IntelliJ\n1. Extract to a NEW folder; do not overwrite an existing repository.\n2. Open pom.xml as a Maven project and choose JDK 21.\n3. Reload Maven; first dependency download requires internet.\n4. Configure local infrastructure and the required environment variables below.\n5. Run the Application main class.\n\n## Build\nWindows: `.\\mvnw.cmd clean package`\nLinux/macOS: `chmod +x mvnw && ./mvnw clean package`\nNo generated business tests yet: a successful package is not an integration test.\n\n## Local prerequisites\n"+
            ((deps.contains("jpa") || deps.contains("flyway"))?"- Install the selected SQL database locally; create the database named in application.yml. Set DB_USERNAME and DB_PASSWORD in IntelliJ Run Configuration. DB_URL optionally overrides the URL. No Docker.\n- Add Flyway SQL migrations before adding JPA entities; ddl-auto=validate prevents accidental schema creation.\n":"")+
            (deps.contains("mongodb")?"- Start local MongoDB. Default database: technotes_notes; override with MONGODB_URI.\n":"")+
            (deps.contains("oauth2-authorization-server")?"- OAuth is NOT implemented by this scaffold. Implement SecurityFilterChain, PKCE client registration, persistent authorization storage and signing keys, private owner provisioning, token claims/validation and /api/v1/users/me. See docs/FIXED-OAUTH-CONTRACT.md for the OAuth preset. Do not use generated development passwords as owner credentials.\n":"")+
            (deps.contains("gateway")?"- Add reviewed gateway routes before UI integration; no routes are generated.\n":"")+
            "\n## Version changes\nChange versions.properties in the generator source, verify compatibility, rebuild it, then review upgrades separately in existing repositories. Never assume arbitrary custom dependencies are BOM-managed; give a version when needed.\n";
    }
    private static Properties versions() {
        Properties p=new Properties();
        try(InputStream in=LocalInitializr.class.getResourceAsStream("/versions.properties")) { if(in==null)throw new IOException("Missing versions.properties");p.load(in); }
        catch(IOException e){throw new ExceptionInInitializerError(e);}
        for(String k:List.of("boot","cloud","admin","java")) if(p.getProperty(k)==null || !p.getProperty(k).matches("[0-9.]+")) throw new IllegalArgumentException("Invalid version: "+k);
        return p;
    }
    private static String catalogJson() {
        StringBuilder b=new StringBuilder("{\"boot\":"+json(BOOT)+",\"cloud\":"+json(CLOUD)+",\"java\":"+json(JAVA)+",\"presets\":[");
        for(int i=0;i<PRESETS.size();i++) { Preset p=PRESETS.get(i); if(i>0)b.append(','); b.append("{\"id\":"+json(p.id)+",\"label\":"+json(p.label)+",\"artifact\":"+json(p.artifact)+",\"pkg\":"+json(p.pkg)+",\"port\":"+p.port+",\"deps\":"+json(p.deps)+",\"note\":"+json(p.note)+"}"); }
        b.append("],\"dependencies\":[");
        for(int i=0;i<CATALOG.length;i++){if(i>0)b.append(',');b.append("{\"id\":"+json(CATALOG[i][0])+",\"label\":"+json(CATALOG[i][1])+"}");}
        return b.append("]}").toString();
    }
    private static String json(String s) { return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\""; }
    private static String resource(String path)throws IOException {
        try(InputStream in=LocalInitializr.class.getResourceAsStream("/"+path)){if(in==null)throw new IOException("Missing resource "+path);return new String(in.readAllBytes(),StandardCharsets.UTF_8);}
    }
    private static boolean validCoords(String c) {
        String[] p=c.split(":",-1);
        return p.length>=2 && p.length<=4 && p[0].matches("[A-Za-z0-9_.-]+") && p[1].matches("[A-Za-z0-9_.-]+") &&
            (p.length<3 || p[2].matches("[A-Za-z0-9_.+-]*")) && (p.length<4 || Set.of("","compile","provided","runtime","test").contains(p[3]));
    }
    private static Map<String,String> parseQuery(String raw) {
        Map<String,String> m=new LinkedHashMap<>();
        for(String p:raw.split("&")){String[] kv=p.split("=",2);String k=URLDecoder.decode(kv[0],StandardCharsets.UTF_8);if(m.containsKey(k))throw new IllegalArgumentException("Duplicate form field");m.put(k,kv.length<2?"":URLDecoder.decode(kv[1],StandardCharsets.UTF_8));}return m;
    }
    private static String toClassName(String a) { StringBuilder b=new StringBuilder();for(String p:a.split("-")) b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));return b.toString(); }
    private static String xml(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static void put(ZipOutputStream z,String path,String s)throws IOException {ZipEntry e=new ZipEntry(path);e.setTime(0);z.putNextEntry(e);z.write(s.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
    private static void send(HttpExchange e,int status,String type,String s)throws IOException {byte[] b=s.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type",type);e.sendResponseHeaders(status,b.length);e.getResponseBody().write(b);}
}
