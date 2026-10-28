package bff.servlet;

import bff.dto.JsonResponse;
import bff.service.ServiceException;
import bff.service.UserService;
import bff.service.impl.UserServiceRestImpl;
import bff.web.Tokens;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

@WebServlet({"/logout", "/api/auth/logout"})
public class LogoutServlet extends BaseServlet {
    private UserService userService;

    @Override
    public void init() {
        userService = new UserServiceRestImpl();
    }




    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!"/logout".equals(req.getServletPath())) {
            writeError(resp, 405, "Используйте POST");
            return;
        }
        resp.setContentType("text/html;charset=UTF-8");
        resp.getWriter().write("<!DOCTYPE html><html lang=\"ru\"><meta charset=\"UTF-8\">"
                + "<title>Выход</title><h1>Выйти из аккаунта?</h1>"
                + "<form method=\"post\"><button type=\"submit\">Выйти</button></form></html>");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String jwt = Tokens.get(req);
        JsonResponse result = null;
        ServiceException failure = null;
        try {
            if (jwt != null) result = userService.logout(jwt);
        } catch (ServiceException e) {
            failure = e;
        } finally {
            HttpSession session = req.getSession(false);
            if (session != null) session.invalidate();
        }

        if (failure != null) {
            writeError(resp, failure.status(), "Сессия BFF закрыта, но отзыв токена не подтверждён: сервис пользователей недоступен");
            return;
        }
        if (result != null && result.status() != 401 && (result.status() < 200 || result.status() >= 300)) {
            writeError(resp, result.status(), "Сессия BFF закрыта, но User Service не подтвердил отзыв токена");
            return;
        }

        if ("/api/auth/logout".equals(req.getServletPath())) resp.setStatus(204);
        else {
            resp.setStatus(303);
            resp.setHeader("Location", req.getContextPath() + "/login.jsp");
        }
    }
}
