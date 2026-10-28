package ru.kicksharing.documents.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.kicksharing.documents.dto.request.GenerateDocumentRequest;
import ru.kicksharing.documents.exception.ApiException;
import ru.kicksharing.documents.service.DocumentService;
import ru.kicksharing.documents.util.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@WebServlet("/api/documents/*")
public class DocumentServlet extends HttpServlet {
    private final DocumentService documentService = new DocumentService();

    private static int integer(HttpServletRequest req, String key, int fallback) {
        String text = req.getParameter(key);
        if (text == null) return fallback;
        try {
            if (!text.matches("[0-9]{1,10}")) throw new IllegalArgumentException();
            return Integer.parseInt(text);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Некорректный " + key);
        }
    }

    private static java.io.PrintWriter json(HttpServletResponse resp, int status) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        return resp.getWriter();
    }

    private static void pdf(HttpServletResponse resp, byte[] pdf, String name) throws IOException {
        resp.setStatus(200);
        resp.setContentType("application/pdf");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + name + ".pdf\"");
        resp.setContentLength(pdf.length);
        resp.getOutputStream().write(pdf);
    }

    private static void error(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(JsonUtil.toJson(java.util.Map.of("error", message)));
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (ru.kicksharing.documents.util.BrowserNavigation.reject(req, resp)) return;
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        String method = req.getMethod();
        if (path.equals("/generate")) {
            if (!method.equals("POST")) {
                resp.setHeader("Allow", "POST");
                error(resp, 405, "Метод не поддерживается");
                return;
            }
            doPost(req, resp);
            return;
        }
        try {
            String auth = req.getHeader("Authorization");
            if (path.isEmpty() || path.equals("/")) {
                if (!method.equals("GET")) throw new ApiException(405, "Метод не поддерживается");
                JsonUtil.getMapper().writeValue(json(resp, 200), documentService.history(auth, integer(req, "limit", 50), integer(req, "offset", 0)));
                return;
            }
            String[] parts = path.substring(1).split("/", -1);
            if (parts.length < 1 || parts.length > 2 || !parts[0].matches("[0-9a-fA-F-]{36}"))
                throw ApiException.notFound("Путь не найден");
            java.util.UUID id;
            try {
                id = java.util.UUID.fromString(parts[0]);
            } catch (IllegalArgumentException e) {
                throw ApiException.notFound("Путь не найден");
            }
            if (parts.length == 2) {
                if (!parts[1].equals("download")) throw ApiException.notFound("Путь не найден");
                if (!method.equals("GET")) throw new ApiException(405, "Метод не поддерживается");
                pdf(resp, documentService.download(auth, id), "document");
                return;
            }
            if (method.equals("GET"))
                JsonUtil.getMapper().writeValue(json(resp, 200), documentService.metadata(auth, id));
            else if (method.equals("DELETE")) {
                documentService.delete(auth, id);
                resp.setStatus(204);
            } else throw new ApiException(405, "Метод не поддерживается");
        } catch (ApiException e) {
            error(resp, e.getStatusCode(), e.getMessage());
        } catch (Exception e) {
            getServletContext().log("Document metadata request failed", e);
            error(resp, 500, "Внутренняя ошибка сервера");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
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
            try {
                request = ru.kicksharing.documents.util.DocumentInput.parse(new String(bytes, StandardCharsets.UTF_8));
            } catch (RuntimeException e) {
                throw ApiException.badRequest("Некорректный JSON");
            }
            if (request == null || request.getUserId() == null || request.getDocType() == null || request.getDocType().isBlank())
                throw ApiException.badRequest("userId и docType обязательны");
            byte[] pdf = documentService.generate(request, auth);
            String name = "RECEIPT".equals(request.getDocType()) ? "ride_payment" : request.getDocType().toLowerCase(Locale.ROOT);
            resp.setStatus(200);
            resp.setContentType("application/pdf");
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + name + ".pdf\"");
            resp.setContentLength(pdf.length);
            resp.getOutputStream().write(pdf);
        } catch (ApiException e) {
            error(resp, e.getStatusCode(), e.getMessage());
        } catch (Exception e) {
            getServletContext().log("Document generation failed", e);
            error(resp, 500, "Внутренняя ошибка сервера");
        }
    }
}
