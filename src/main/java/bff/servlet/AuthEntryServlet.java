package bff.servlet;

import bff.service.UserService;
import bff.service.ServiceException;
import bff.service.impl.UserServiceRestImpl;
import bff.web.Tokens;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Browser login entry: reuse a verified session; never revoke a token on GET. */
@WebServlet("/auth/entry")
public final class AuthEntryServlet extends BaseServlet {
    private UserService users;
    private String panel;
    @Override public void init() throws ServletException {
        users = new UserServiceRestImpl(); panel = FrontendConfigServlet.configuredUrl();
    }
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String token = Tokens.get(req);
        if (token == null) { redirect(resp, req.getContextPath() + "/?auth=login"); return; }
        try {
            var user = users.currentUser(token);
            if (user.deleted()) { redirect(resp, req.getContextPath() + "/?auth=login"); return; }
            if ("ADMIN".equals(user.role()))
                redirect(resp, panel + "#accessToken=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
            else redirect(resp, req.getContextPath() + "/?account=profile");
        } catch (ServiceException e) {
            if (e.status() == 401) redirect(resp, req.getContextPath() + "/?auth=login");
            else writeServiceError(resp, e);
        }
    }
    private static void redirect(HttpServletResponse resp, String location) {
        resp.setStatus(302); resp.setHeader("Location", location);
        resp.setHeader("Cache-Control", "no-store"); resp.setHeader("Referrer-Policy", "no-referrer");
    }
}
