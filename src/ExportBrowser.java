import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Export the Java dependency catalog as build-time browser templates. */
public class ExportBrowser {
    static Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method m=LocalInitializr.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);
    }
    static String quote(String s) throws Exception {return (String)call("json",new Class[]{String.class},s);}
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args.length>0?args[0]:"site");Files.createDirectories(out);
        String catalog=(String)call("catalogJson",new Class[]{});
        Files.writeString(out.resolve("catalog.json"),catalog);
        Field f=LocalInitializr.class.getDeclaredField("CATALOG");f.setAccessible(true);
        StringBuilder modules=new StringBuilder("{");
        List<String> keys=new ArrayList<>();keys.add("base");for(String[] d:(String[][])f.get(null))keys.add(d[0]);
        keys.add("flyway-postgresql");keys.add("flyway-mysql");
        for(String key:keys){
            Set<String> deps=key.equals("base")?Set.of():key.equals("flyway-postgresql")?Set.of("flyway","postgresql"):key.equals("flyway-mysql")?Set.of("flyway","mysql"):Set.of(key);
            String pom=(String)call("pom",new Class[]{String.class,String.class,String.class,String.class,Set.class,String.class},"com.technotes","placeholder","placeholder","21",deps,"");
            if(modules.length()>1)modules.append(',');modules.append(quote(key)).append(':').append(quote(pom));
        }
        Files.writeString(out.resolve("modules.json"),modules.append('}').toString());
        for(String name:List.of("index.html","style.css","app.js")) Files.copy(Path.of("resources",name),out.resolve(name),StandardCopyOption.REPLACE_EXISTING);
        for(String name:List.of("engine.js","zip.js"))Files.copy(Path.of("browser",name),out.resolve(name),StandardCopyOption.REPLACE_EXISTING);
        Path templates=out.resolve("templates");Files.createDirectories(templates);
        for(String name:List.of("mvnw","mvnw.cmd","maven-wrapper.properties"))Files.copy(Path.of("resources/wrapper",name),templates.resolve(name),StandardCopyOption.REPLACE_EXISTING);
        Files.copy(Path.of("resources/OAUTH-CONTRACT.md"),templates.resolve("OAUTH-CONTRACT.md"),StandardCopyOption.REPLACE_EXISTING);
        String html=Files.readString(out.resolve("index.html")).replace("href=\"/\"","href=\"./\"").replace("href=\"/style.css\"","href=\"./style.css\"").replace("<script defer src=\"/app.js\"></script>","<script defer src=\"./zip.js\"></script><script defer src=\"./engine.js\"></script><script defer src=\"./app.js\"></script>");
        html=html.replace("Local-first development.","Generated privately in your browser.");
        Files.writeString(out.resolve("index.html"),html);
        String js=Files.readString(out.resolve("app.js")).replace("fetch(","window.initializrFetch(");Files.writeString(out.resolve("app.js"),js);
        Files.writeString(out.resolve(".nojekyll"),"");
        String revision=System.getenv().getOrDefault("GITHUB_SHA","local-build");Files.writeString(out.resolve("build.json"),"{\"revision\":"+quote(revision)+"}");
        System.out.println("Browser site exported to "+out);
    }
}
