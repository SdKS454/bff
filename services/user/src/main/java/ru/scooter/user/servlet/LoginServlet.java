package ru.scooter.user.servlet;

import jakarta.servlet.http.*;

import ru.scooter.user.dto.LoginRequest;
import ru.scooter.user.util.Json;

import java.io.IOException;

public final class LoginServlet extends BaseServlet {
    @Override
    protected void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireMethod(req, resp, "POST");
        Json.write(resp, 200, components.auth().login(Json.read(req, LoginRequest.class)));
    }
}
