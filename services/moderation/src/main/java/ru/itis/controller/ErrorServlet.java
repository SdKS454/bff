package ru.itis.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.itis.util.Util;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/error")
public class ErrorServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(ErrorServlet.class.getName());

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (ru.itis.util.BrowserNavigation.reject(req, resp)) return;
        Integer code = (Integer) req.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Throwable exception = (Throwable) req.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        if (exception != null) {
            LOG.log(Level.SEVERE, "Необработанное исключение", exception);
        }

        if (code == null || code == 404) {
            Util.sendError(resp, 404, "Не найдено");
        } else if (code == 405) {
            Util.sendError(resp, 405, "Метод не поддерживается");
        } else {
            Util.sendError(resp, 500, "Внутренняя ошибка");
        }
    }
}
