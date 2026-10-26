package ru.scooter.user.servlet;

import jakarta.servlet.http.*;

import ru.scooter.user.util.Json;

import java.io.IOException;

public final class NotFoundServlet extends BaseServlet {
    @Override
    protected void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Json.error(resp, 404, "Ручка не найдена");
    }
}
