package ru.scooter.user.servlet;

import jakarta.servlet.http.*;

import ru.scooter.user.dto.RegisterRequest;
import ru.scooter.user.util.Json;

import java.io.IOException;

public final class RegisterServlet extends BaseServlet {
    @Override
    protected void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireMethod(req, resp, "POST");
        Json.write(resp, 201, components.auth().register(Json.read(req, RegisterRequest.class)));
    }
}
