package ru.scooter.user;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

import java.nio.file.Files;
import java.nio.file.Path;




public final class WarTestServer {
    public static void main(String[] args) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(Files.createTempDirectory("user-tomcat-").toString());
        tomcat.setPort(0);
        tomcat.getConnector();
        Context context =
                tomcat.addWebapp(
                        "", Path.of("target/user-service.war").toAbsolutePath().toString());
        context.setParentClassLoader(WarTestServer.class.getClassLoader());
        tomcat.start();
        if (!context.getState().isAvailable())
            throw new IllegalStateException("WAR startup failed");
        System.out.println("USER_TEST_PORT=" + tomcat.getConnector().getLocalPort());
        System.out.flush();
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    try {
                                        tomcat.stop();
                                        tomcat.destroy();
                                    } catch (Exception ignored) {
                                    }
                                }));
        tomcat.getServer().await();
    }
}
