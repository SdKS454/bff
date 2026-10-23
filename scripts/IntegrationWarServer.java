import org.apache.catalina.startup.Tomcat;
import java.nio.file.*;

/** Standalone real WAR for an optional local cross-service check; binds loopback only. */
public final class IntegrationWarServer {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: IntegrationWarServer.java WAR");
        Path base = Files.createTempDirectory("bff-external-war-");
        Files.createDirectories(base.resolve("webapps"));
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(base.toString());
        tomcat.setPort(Integer.getInteger("integration.port", 0));
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        var app = tomcat.addWebapp("", Path.of(args[0]).toAbsolutePath().toString());
        app.setParentClassLoader(IntegrationWarServer.class.getClassLoader());
        tomcat.start();
        if (!app.getState().isAvailable()) {
            tomcat.stop(); tomcat.destroy();
            throw new IllegalStateException("WAR failed to deploy");
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { tomcat.stop(); tomcat.destroy(); }
            catch (Exception e) { System.err.println("Integration WAR shutdown failed"); }
        }));
        System.out.println("WAR_URL=http://127.0.0.1:" + tomcat.getConnector().getLocalPort());
        System.out.flush();
        tomcat.getServer().await();
    }
}
