package ru.scooter.user.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.scooter.user.filter.AuthFilter;

import java.io.IOException;

public final class LogoutServlet extends BaseServlet {
    @Override
    protected void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireMethod(req, resp, "POST");
        components.auth().logout(AuthFilter.principal(req));
        resp.setStatus(204);
    }
}
