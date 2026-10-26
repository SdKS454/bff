package ru.scooter.user.servlet;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import ru.scooter.user.config.*;
import ru.scooter.user.exception.ApiException;

import java.io.IOException;

public abstract class BaseServlet extends HttpServlet {
    protected AppComponents components;

    @Override
    public void init() {
        components = AppContextListener.components(getServletContext());
    }

    @Override
    protected final void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        handle(req, resp);
    }

    protected abstract void handle(HttpServletRequest req, HttpServletResponse resp)
            throws IOException;

    protected void requireMethod(HttpServletRequest req, HttpServletResponse resp, String method) {
        if (!method.equals(req.getMethod())) {
            resp.setHeader("Allow", method + ", OPTIONS");
            throw new ApiException(405, "Метод не поддерживается");
        }
    }
}
