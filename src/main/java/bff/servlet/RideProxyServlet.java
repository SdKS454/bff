package bff.servlet;

import bff.dto.EndRideRequest;
import bff.dto.JsonResponse;
import bff.dto.StartRideRequest;
import bff.dto.UserDto;
import bff.service.RideService;
import bff.service.ServiceException;
import bff.service.UserService;
import bff.service.impl.JsonText;
import bff.service.impl.RideServiceRestImpl;
import bff.service.impl.UserServiceRestImpl;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/api/rides/*")
public class RideProxyServlet extends BaseServlet {

    private RideService rideService;
    private UserService userService;

    @Override
    public void init() throws ServletException {
        rideService = new RideServiceRestImpl();
        userService = new UserServiceRestImpl();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String jwt = requireJwt(req, resp);
        if (jwt == null) {
            return;
        }
        if (req.getPathInfo() != null && !"/".equals(req.getPathInfo())) {
            writeError(resp, 404, "Не найдено");
            return;
        }
        try {
            UserDto me = userService.currentUser(jwt);
            JsonResponse rides = rideService.findByUser(jwt, me.id());
            writeJson(resp, rides.status(), rides.body());
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

        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        req.setCharacterEncoding("UTF-8");
        try {
            if (path.isEmpty() || "/".equals(path)) {
                StartRideRequest body = readStart(req);
                UserDto me = userService.currentUser(jwt);
                if (me.deleted() || userService.isBlocked(me.id(), jwt)) {
                    writeError(resp, HttpServletResponse.SC_FORBIDDEN, "Пользователь заблокирован");
                    return;
                }
                JsonResponse started = rideService.start(jwt, body);
                writeJson(resp, started.status(), started.body());
                return;
            }
            if (path.matches("^/[^/]+/pay$")) {
                java.util.UUID id;
                try {
                    id = JsonText.uuid(path.split("/")[1]);
                } catch (IllegalArgumentException e) {
                    writeError(resp, 404, "Не найдено");
                    return;
                }
                JsonResponse paid = rideService.pay(jwt, id);
                writeJson(resp, paid.status(), paid.body());
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

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String jwt = requireJwt(req, resp);
        if (jwt == null) {
            return;
        }

        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        req.setCharacterEncoding("UTF-8");
        try {
            if (path.matches("^/[^/]+/review$")) {
                java.util.UUID id = JsonText.uuid(path.split("/")[1]);
                String json = JsonText.read(req.getInputStream());
                JsonText.object(json);
                JsonResponse reviewed = rideService.review(jwt, id, json);
                writeJson(resp, reviewed.status(), reviewed.body());
                return;
            }
            if (path.matches("^/[^/]+/(finish|position)$")) {
                java.util.UUID id;
                try {
                    id = JsonText.uuid(path.split("/")[1]);
                } catch (IllegalArgumentException e) {
                    writeError(resp, 404, "Не найдено");
                    return;
                }
                EndRideRequest body = readFinish(req);
                JsonResponse finished = path.endsWith("/position") ? rideService.position(jwt, id, body) : rideService.finish(jwt, id, body);
                if (finished.status() == 204) resp.setStatus(204);
                else writeJson(resp, finished.status(), finished.body());
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

    private StartRideRequest readStart(HttpServletRequest req) throws IOException {
        String json = JsonText.read(req.getInputStream());
        return new StartRideRequest(
                JsonText.requiredString(json, "numberPlate"),
                JsonText.uuidOrNull(json, "rateId"),
                JsonText.coordinate(json, "posX", false),
                JsonText.coordinate(json, "posY", false));
    }

    private EndRideRequest readFinish(HttpServletRequest req) throws IOException {
        String json = JsonText.read(req.getInputStream());
        return new EndRideRequest(JsonText.coordinate(json, "posX", true), JsonText.coordinate(json, "posY", true));
    }

}
