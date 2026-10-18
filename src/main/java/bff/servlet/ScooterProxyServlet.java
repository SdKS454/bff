package bff.servlet;

import bff.dto.JsonResponse;
import bff.dto.NearestScooterRequest;
import bff.service.ScooterService;
import bff.service.ServiceException;
import bff.service.impl.JsonText;
import bff.service.impl.ScooterServiceRestImpl;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/api/scooters/*")
public class ScooterProxyServlet extends BaseServlet {

    private ScooterService scooterService;

    @Override
    public void init() throws ServletException {
        scooterService = new ScooterServiceRestImpl();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String jwt = requireJwt(req, resp);
        if (jwt == null) {
            return;
        }

        String path = req.getPathInfo();
        try {
            if (path == null || "/".equals(path)) {
                boolean ready = "READY".equalsIgnoreCase(req.getParameter("status"))
                        || "true".equalsIgnoreCase(req.getParameter("available"));
                JsonResponse list = scooterService.list(jwt, ready);
                writeJson(resp, list.status(), list.body());
                return;
            }
            if ("/nearest".equals(path)) {
                writeError(resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                        "Используйте POST /api/scooters/nearest с posX и posY");
                return;
            }

            String numberPlate = path.substring(1);
            if (numberPlate.isBlank()) {
                writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "Укажите номер самоката");
                return;
            }
            JsonResponse scooter = scooterService.findByNumberPlate(jwt, numberPlate);
            writeJson(resp, scooter.status(), scooter.body());
        } catch (ServiceException e) {
            writeServiceError(resp, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String jwt = requireJwt(req, resp);
        if (jwt == null) {
            return;
        }
        if (!"/nearest".equals(req.getPathInfo())) {
            writeError(resp, HttpServletResponse.SC_NOT_FOUND, "Не найдено");
            return;
        }

        req.setCharacterEncoding("UTF-8");
        try {
            NearestScooterRequest body = readNearest(req);
            if (body.posX() == null || body.posY() == null) {
                writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "Нужны posX и posY");
                return;
            }
            JsonResponse nearest = scooterService.nearest(jwt, body);
            writeJson(resp, nearest.status(), nearest.body());
        } catch (ServiceException e) {
            writeServiceError(resp, e);
        } catch (IllegalArgumentException e) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    e.getMessage() != null ? e.getMessage() : "Некорректный запрос");
        }
    }

    private NearestScooterRequest readNearest(HttpServletRequest req) throws IOException {
        String json = JsonText.read(req.getInputStream());
        return new NearestScooterRequest(JsonText.coordinate(json, "posX", true), JsonText.coordinate(json, "posY", true));
    }

}
