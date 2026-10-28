package ru.scooter.user.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.scooter.user.dto.AuthPrincipal;
import ru.scooter.user.dto.BanRequest;
import ru.scooter.user.exception.ApiException;
import ru.scooter.user.filter.AuthFilter;
import ru.scooter.user.util.Json;
import ru.scooter.user.util.Validation;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class UsersServlet extends BaseServlet {
    @Override
    protected void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        if (path == null || path.equals("/")) {
            requireMethod(req, resp, "GET");
            Json.write(resp, 200, components.users().list(AuthFilter.principal(req)));
            return;
        }
        switch (path) {
            case "/exists" -> {
                requireMethod(req, resp, "GET");
                Json.write(
                        resp,
                        200,
                        Map.of("exists", components.users().exists(req.getParameter("login"))));
            }
            case "/validate" -> {
                requireMethod(req, resp, "GET");
                resp.setStatus(204);
            }
            case "/me" -> {
                requireMethod(req, resp, "GET");
                Json.write(resp, 200, components.users().me(AuthFilter.principal(req)));
            }
            case "/blocked" -> {
                requireMethod(req, resp, "GET");
                Json.write(
                        resp,
                        200,
                        components
                                .moderation()
                                .list(
                                        req.getParameter("limit"),
                                        req.getParameter("offset"),
                                        AuthFilter.principal(req)));
            }
            case "/reasons" -> {
                requireMethod(req, resp, "GET");
                Json.write(resp, 200, components.moderation().reasons(AuthFilter.principal(req)));
            }
            default -> handleUser(path, req, resp);
        }
    }

    private void handleUser(String path, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String[] parts = path.substring(1).split("/", -1);
        if (parts.length > 2 || parts[0].isEmpty()) throw new ApiException(404, "Ручка не найдена");
        if (parts.length == 2 && !Set.of("blocked", "ban", "unban").contains(parts[1]))
            throw new ApiException(404, "Ручка не найдена");
        UUID id = Validation.uuid(parts[0]);
        AuthPrincipal principal = AuthFilter.principal(req);
        if (parts.length == 1) {
            requireMethod(req, resp, "GET");
            Json.write(resp, 200, components.users().byId(id, principal));
            return;
        }
        switch (parts[1]) {
            case "blocked" -> {
                requireMethod(req, resp, "GET");
                Json.write(resp, 200, Map.of("blocked", components.users().blocked(id, principal)));
            }
            case "ban" -> {
                requireMethod(req, resp, "POST");
                Json.write(
                        resp,
                        200,
                        components
                                .moderation()
                                .ban(id, Json.read(req, BanRequest.class), principal));
            }
            case "unban" -> {
                requireMethod(req, resp, "POST");
                Json.write(resp, 200, components.moderation().unban(id, principal));
            }
            default -> throw new ApiException(404, "Ручка не найдена");
        }
    }
}
