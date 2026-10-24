package ru.kicksharing.documents.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import ru.kicksharing.documents.dto.request.GenerateDocumentRequest;
import ru.kicksharing.documents.exception.ApiException;
import ru.kicksharing.documents.service.DocumentService;
import ru.kicksharing.documents.util.JsonUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@WebServlet("/api/documents/generate")
public class DocumentServlet extends HttpServlet {
    private final DocumentService documentService = new DocumentService();

    @Override protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        if (!"POST".equals(req.getMethod())) {
            resp.setHeader("Allow", "POST"); error(resp, 405, "Метод не поддерживается"); return;
        }
        doPost(req, resp);
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String auth = req.getHeader("Authorization");
            if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, 7) || auth.substring(7).isBlank())
                throw new ApiException(401, "Ожидается Bearer JWT");
            String type = req.getContentType();
            if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase("application/json"))
                throw new ApiException(415, "Ожидается application/json");
            byte[] bytes = req.getInputStream().readNBytes(65537);
            if (bytes.length > 65536) throw new ApiException(413, "Тело запроса слишком велико");
            GenerateDocumentRequest request;
            try { request = ru.kicksharing.documents.util.DocumentInput.parse(new String(bytes, StandardCharsets.UTF_8)); }
            catch (RuntimeException e) { throw ApiException.badRequest("Некорректный JSON"); }
            if (request == null || request.getUserId() == null || request.getDocType() == null || request.getDocType().isBlank())
                throw ApiException.badRequest("userId и docType обязательны");
            byte[] pdf = documentService.generate(request, auth);
            String name = "RECEIPT".equals(request.getDocType()) ? "ride_payment" : request.getDocType().toLowerCase(Locale.ROOT);
            resp.setStatus(200);
            resp.setContentType("application/pdf");
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + name + ".pdf\"");
            resp.setContentLength(pdf.length);
            resp.getOutputStream().write(pdf);
        } catch (ApiException e) { error(resp, e.getStatusCode(), e.getMessage()); }
        catch (Exception e) {
            getServletContext().log("Document generation failed", e);
            error(resp, 500, "Внутренняя ошибка сервера");
        }
    }

    private static void error(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(JsonUtil.toJson(java.util.Map.of("error", message)));
    }
}
