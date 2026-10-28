package ru.kicksharing.documents.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thymeleaf.context.Context;
import ru.kicksharing.documents.dto.internal.RidePaymentDto;
import ru.kicksharing.documents.dto.internal.UserBlockedDto;
import ru.kicksharing.documents.dto.internal.UserHistoryDto;
import ru.kicksharing.documents.exception.ApiException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfServiceTest {
    static String font() {
        String configured = System.getenv("PDF_FONT_PATH");
        if (configured == null) configured = "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf";
        assertTrue(java.nio.file.Files.isRegularFile(java.nio.file.Path.of(configured)), "Set PDF_FONT_PATH to a Cyrillic TTF font to run PDF tests");
        return configured;
    }

    @ParameterizedTest
    @ValueSource(strings = {"ride-payment", "user-blocked", "user-history"})
    void allTemplatesProduceReadableRussianPdf(String template) throws Exception {
        System.setProperty("documents.pdf.font.path", font());
        var context = new Context();
        if (template.equals("ride-payment")) {
            var doc = new RidePaymentDto();
            doc.setUserFullName("Иван Петров");
            doc.setUserLogin("ivan");
            doc.setCurrency("RUB");
            context.setVariable("doc", doc);
        } else if (template.equals("user-blocked")) {
            var doc = new UserBlockedDto();
            doc.setUserFullName("Иван Петров");
            doc.setReason("Опасное вождение");
            context.setVariable("doc", doc);
        } else {
            var doc = new UserHistoryDto();
            doc.setUserFullName("Иван Петров");
            doc.setRides(List.of());
            doc.setFrom(LocalDate.now());
            doc.setTo(LocalDate.now());
            context.setVariable("doc", doc);
        }
        byte[] pdf = new PdfService().generatePdf(template, context);
        try (var document = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Иван Петров"), text);
            assertTrue(document.getNumberOfPages() > 0);
        }
    }

    @Test
    void missingFontMustNotReturnUnreadablePdf() {
        System.setProperty("documents.pdf.font.path", "/missing/font.ttf");
        var context = new Context();
        context.setVariable("doc", new UserBlockedDto());
        try {
            assertEquals(503, assertThrows(ApiException.class, () -> new PdfService().generatePdf("user-blocked", context)).getStatusCode());
        } finally {
            System.clearProperty("documents.pdf.font.path");
        }
    }
}
