import org.apache.catalina.startup.Tomcat;
import java.nio.file.*;

/** Optional local integration harness. Both WARs are real; User DB/env must already be configured. */
public final class RealUserIntegrationServer {
    private static Tomcat start(String context, Path war) throws Exception {
        Path base = Files.createTempDirectory("bff-user-integration-");
        Files.createDirectories(base.resolve("webapps"));
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(base.toString());
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        var app = tomcat.addWebapp(context, war.toAbsolutePath().toString());
        app.setParentClassLoader(RealUserIntegrationServer.class.getClassLoader());
        tomcat.start();
        if (!app.getState().isAvailable()) {
            tomcat.stop(); tomcat.destroy();
            throw new IllegalStateException("WAR failed to deploy: " + war.getFileName());
        }
        return tomcat;
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: RealUserIntegrationServer.java USER_WAR BFF_WAR");
        Tomcat user = start("", Path.of(args[0]));
        String userUrl = "http://127.0.0.1:" + user.getConnector().getLocalPort();
        System.setProperty("user.service.url", userUrl);
        Tomcat bff;
        try { bff = start(System.getProperty("bff.test.context", "/scooter-bff"), Path.of(args[1])); }
        catch (Exception e) { user.stop(); user.destroy(); throw e; }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { bff.stop(); bff.destroy(); }
            catch (Exception e) { System.err.println("BFF shutdown failed"); }
            try { user.stop(); user.destroy(); }
            catch (Exception e) { System.err.println("User shutdown failed"); }
        }));
        System.out.println("USER_URL=" + userUrl);
        System.out.println("BFF_URL=http://127.0.0.1:" + bff.getConnector().getLocalPort() + System.getProperty("bff.test.context", "/scooter-bff"));
        System.out.flush();
        bff.getServer().await();
    }
}
