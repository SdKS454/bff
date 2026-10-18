package bff.servlet;

import bff.dto.AuthRequest;
import bff.dto.AuthResponse;
import bff.dto.JsonResponse;
import bff.dto.UserDto;
import bff.service.ServiceException;
import bff.service.UserService;
import bff.service.impl.JsonText;
import bff.service.impl.UserServiceRestImpl;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

@WebServlet("/api/auth/*")
public class AuthServlet extends BaseServlet {

    private UserService userService;

    @Override
    public void init() throws ServletException {
        userService = new UserServiceRestImpl();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String action = req.getPathInfo();
        if (!"/register".equals(action) && !"/login".equals(action)) {
            writeError(resp, 404, "Не найдено");
            return;
        }
        try {
            AuthRequest body = readAuth(req);
            if ("/register".equals(action)) {
                JsonResponse registered = userService.register(body);
                writeJson(resp, registered.status(), registered.body());
                return;
            }
            if ("/login".equals(action)) {
                AuthResponse auth = userService.login(body);
                HttpSession session = req.getSession(true);
                req.changeSessionId();
                session.setAttribute("jwt", auth.accessToken());
                if (auth.expiresInSeconds() > 0 && auth.expiresInSeconds() <= Integer.MAX_VALUE) {
                    session.setMaxInactiveInterval((int) auth.expiresInSeconds());
                }
                writeJson(resp, HttpServletResponse.SC_OK, toJson(auth));
                return;
            }
            writeError(resp, HttpServletResponse.SC_NOT_FOUND, "Не найдено");
        } catch (ServiceException e) {
            writeServiceError(resp, e);
        } catch (IllegalArgumentException e) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    e.getMessage() != null ? e.getMessage() : "Некорректный запрос");
        }
    }

    private AuthRequest readAuth(HttpServletRequest req) throws IOException {
        String json = JsonText.read(req.getInputStream());
        return new AuthRequest(
                JsonText.requiredString(json, "login"),
                JsonText.requiredString(json, "password"),
                JsonText.string(json, "fullName"),
                JsonText.string(json, "birthDate"),
                JsonText.string(json, "avatarUrl"));
    }

    private String toJson(AuthResponse auth) {
        return "{\"accessToken\":" + JsonText.quote(auth.accessToken())
                + ",\"expiresInSeconds\":" + auth.expiresInSeconds()
                + ",\"user\":" + userJson(auth.user()) + "}";
    }

    private String userJson(UserDto user) {
        if (user == null) {
            return "null";
        }
        return "{\"id\":" + JsonText.quote(user.id())
                + ",\"login\":" + JsonText.quote(user.login())
                + ",\"role\":" + JsonText.quote(user.role())
                + ",\"fullName\":" + JsonText.quote(user.fullName())
                + ",\"birthDate\":" + JsonText.quote(user.birthDate())
                + ",\"avatarUrl\":" + JsonText.quote(user.avatarUrl())
                + ",\"deleted\":" + user.deleted()
                + ",\"blocked\":" + user.blocked() + "}";
    }

}
