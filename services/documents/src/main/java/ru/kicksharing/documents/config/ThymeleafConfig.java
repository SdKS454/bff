package ru.kicksharing.documents.config;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

public class ThymeleafConfig {
    private static TemplateEngine templateEngine;

    public static synchronized TemplateEngine getTemplateEngine() {
        if (templateEngine == null) {
            ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
            resolver.setPrefix("templates/");
            resolver.setSuffix(".html");
            resolver.setTemplateMode(TemplateMode.HTML);
            resolver.setCharacterEncoding("UTF-8");
            resolver.setCacheable(false);

            templateEngine = new TemplateEngine();
            templateEngine.setTemplateResolver(resolver);
        }
        return templateEngine;
    }
}