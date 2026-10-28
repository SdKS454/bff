package bff.servlet;

import bff.service.ServiceException;
import bff.service.impl.JsonText;
import bff.service.impl.ScooterServiceRestImpl;
import bff.service.impl.UserServiceRestImpl;
import bff.web.Tokens;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(urlPatterns = {"/api/admin/scooters/*", "/admin/scooters"})
public final class AdminScooterProxyServlet extends BaseServlet {
    private final UserServiceRestImpl users = new UserServiceRestImpl();
    private final ScooterServiceRestImpl scooters = new ScooterServiceRestImpl();

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        boolean page = req.getServletPath().equals("/admin/scooters");
        String token = Tokens.get(req);
        if (token == null) {
            if (page) resp.sendRedirect(req.getContextPath() + "/login.jsp");
            else writeError(resp, 401, "Требуется авторизация");
            return;
        }
        try {
            if (!"ADMIN".equals(users.currentUser(token).role())) {
                if (page) resp.sendError(403, "Доступ только для администратора");
                else writeError(resp, 403, "Требуется роль ADMIN");
                return;
            }
            if (page) {
                if (!req.getMethod().equals("GET")) {
                    resp.setStatus(405);
                    return;
                }
                req.getRequestDispatcher("/WEB-INF/admin-scooters.jsp").forward(req, resp);
                return;
            }
            String path = req.getPathInfo();
            if (path == null || path.equals("/")) path = "";
            String method = req.getMethod();
            boolean allowed = path.isEmpty() ? (method.equals("GET") || method.equals("POST")) :
                    path.endsWith("/charge") ? method.equals("POST") : method.equals("DELETE");
            if (!allowed) {
                writeError(resp, 405, "Метод не поддерживается");
                return;
            }
            String body = method.equals("POST") && path.isEmpty() ? JsonText.read(req.getInputStream()) : null;
            var result = scooters.admin(method, path, token, body);
            writeJson(resp, result.status(), result.body());
        } catch (ServiceException e) {
            if (page && e.status() == 401) resp.sendRedirect(req.getContextPath() + "/login.jsp");
            else writeServiceError(resp, e);
        } catch (IllegalArgumentException e) {
            writeError(resp, 400, e.getMessage());
        }
    }
}
