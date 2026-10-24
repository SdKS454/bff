package ru.kicksharing.documents.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import ru.kicksharing.documents.config.ThymeleafConfig;
import ru.kicksharing.documents.config.AppConfig;
import java.io.File;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class PdfService {

    private final TemplateEngine templateEngine = ThymeleafConfig.getTemplateEngine();

    public byte[] generatePdf(String templateName, Context context) {
        // 1. Рендерим HTML
        // Подкладываем CSS в контекст, чтобы вставить его в <style> внутри шаблона
        context.setVariable("inlineCss", loadCss());
        String html = templateEngine.process(templateName, context);

        // 2. Конвертируем HTML -> PDF
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            String fontPath = AppConfig.get("pdf.font.path");
            if (fontPath == null || fontPath.isBlank())
                throw new IllegalStateException("Set PDF_FONT_PATH to a Cyrillic TTF font");
            File font = new File(fontPath);
            if (!font.isFile() || java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, font)
                    .canDisplayUpTo("Документ поездки блокировка") != -1)
                throw new IllegalStateException("A readable Cyrillic TTF font is required");
            builder.useFont(font, "DocumentFont");
            builder.withHtmlContent(html, null); // base URL null, т.к. всё инлайн
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            throw ru.kicksharing.documents.exception.ApiException.serviceUnavailable("Ошибка генерации PDF: проверьте PDF_FONT_PATH и шаблоны");
        }
    }

    private String loadCss() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("static/css/document.css")) {
            if (is == null) throw new IllegalStateException("Не найден static/css/document.css");
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Ошибка загрузки CSS", e);
        }
    }
}