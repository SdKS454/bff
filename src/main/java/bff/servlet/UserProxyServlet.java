package bff.servlet;

import bff.dto.JsonResponse;
import bff.service.ServiceException;
import bff.service.UserService;
import bff.service.impl.JsonText;
import bff.service.impl.UserServiceRestImpl;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/api/users/*")
public class UserProxyServlet extends BaseServlet {

    private UserService userService;

    @Override
    public void init() throws ServletException {
        userService = new UserServiceRestImpl();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();
        try {
            if ("/exists".equals(path)) {
                String login = req.getParameter("login");
                if (login == null || login.isBlank()) {
                    writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "Укажите логин");
                    return;
                }
                JsonResponse exists = userService.exists(login);
                writeJson(resp, exists.status(), exists.body());
                return;
            }
            if ("/me".equals(path)) {
                String jwt = requireJwt(req, resp);
                if (jwt == null) {
                    return;
                }
                JsonResponse profile = userService.profile(jwt);
                writeJson(resp, profile.status(), profile.body());
                return;
            }
            writeError(resp, HttpServletResponse.SC_NOT_FOUND, "Не найдено");
        } catch (ServiceException e) {
            writeServiceError(resp, e);
        }
    }

}
