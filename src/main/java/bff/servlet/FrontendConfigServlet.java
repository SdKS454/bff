package bff.servlet;

import bff.service.impl.JsonText;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;

@WebServlet(value = "/api/config", loadOnStartup = 1)
public class FrontendConfigServlet extends BaseServlet {
    private String moderationPanelUrl;
    @Override public void init() throws ServletException { moderationPanelUrl = configuredUrl(); }
    public static String configuredUrl() throws ServletException {
        String value = System.getProperty("moderation.panel.url");
        if (value == null || value.isBlank()) value = System.getenv("MODERATION_PANEL_URL");
        if (value == null || value.isBlank()) value = "http://localhost:8082/admin/users.html";
        try {
            URI uri = URI.create(value.trim());
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
                throw new IllegalArgumentException();
            return uri.toASCIIString();
        } catch (IllegalArgumentException e) {
            throw new ServletException("moderation.panel.url must be an absolute HTTP(S) URL without credentials or fragment", e);
        }
    }
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        writeJson(resp, 200, "{\"moderationPanelUrl\":" + JsonText.quote(moderationPanelUrl) + "}");
    }
}
