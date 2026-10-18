package bff.servlet;

import bff.service.ServiceException;
import bff.service.impl.JsonText;
import bff.web.Tokens;
import jakarta.servlet.http.*;
import java.io.IOException;

public abstract class BaseServlet extends HttpServlet {
    protected String requireJwt(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String token = Tokens.get(req);
        if (token == null) writeError(resp, 401, "Требуется авторизация");
        return token;
    }
    protected void writeJson(HttpServletResponse resp, int status, String json) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        if (json != null && !json.isEmpty()) resp.getWriter().write(json);
    }
    protected void writeError(HttpServletResponse resp, int status, String message) throws IOException {
        writeJson(resp, status, "{\"error\":" + JsonText.quote(message) + "}");
    }
    protected void writeServiceError(HttpServletResponse resp, ServiceException e) throws IOException {
        String body = e.getMessage() == null ? "Ошибка сервиса" : e.getMessage();
        try {
            JsonText.object(body);
            writeJson(resp, e.status(), body);
        } catch (IllegalArgumentException invalidJson) {
            writeError(resp, e.status(), body);
        }
    }
}
