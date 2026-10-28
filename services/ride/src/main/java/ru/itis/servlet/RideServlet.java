package ru.itis.servlet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.itis.config.DatabaseConfig;
import ru.itis.dto.FinishRideRequest;
import ru.itis.dto.StartRideRequest;
import ru.itis.exception.ApiException;
import ru.itis.repository.RideRepository;
import ru.itis.repository.impl.RideRepositoryJdbcImpl;
import ru.itis.service.RideIntegrationService;
import ru.itis.service.RideService;
import ru.itis.service.impl.RideServiceImpl;

import java.io.IOException;
import java.util.UUID;

@WebServlet("/api/rides/*")
public class RideServlet extends HttpServlet {
    private final ObjectMapper objectMapper = ru.itis.util.Json.MAPPER;
    private final RideService rideService;

    public RideServlet() {
        RideRepository rideRepository = new RideRepositoryJdbcImpl(new DatabaseConfig());
        RideIntegrationService integrationService = new RideIntegrationService();
        rideService = new RideServiceImpl(rideRepository, integrationService);
    }

    public RideServlet(RideService rideService) {
        this.rideService = rideService;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws IOException, ServletException {
        String method = req.getMethod();
        if (!method.equals("GET") && !method.equals("POST") && !method.equals("PUT")) {
            resp.setHeader("Allow", "GET, POST, PUT");
            throw new ApiException(405, "Метод не поддерживается");
        }
        try {
            super.service(req, resp);
        } catch (JsonProcessingException e) {
            throw new ApiException(400, "Некорректный JSON");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if(req.getPathInfo()!=null && !req.getPathInfo().equals("/")) {
            String path=req.getPathInfo().substring(1);
            if(path.contains("/"))throw new ApiException(404,"Путь не найден");
            writeJson(resp,200,rideService.get((UUID)req.getAttribute("userId"),parseUuid(path,404)));return;
        }
        UUID userId = (UUID) req.getAttribute("userId");
        UUID requestedUserId = req.getParameter("userId")==null?userId:parseUuid(req.getParameter("userId"), 400);
        writeJson(resp, 200, rideService.history(userId, requestedUserId));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UUID userId = (UUID) req.getAttribute("userId");
        if (req.getPathInfo() == null || req.getPathInfo().equals("/")) {
            if(Boolean.TRUE.equals(req.getAttribute("blocked")))throw new ApiException(403,"Пользователь заблокирован");
            StartRideRequest body = objectMapper.treeToValue(ru.itis.util.Json.body(req), StartRideRequest.class);
            if (body == null) {
                throw new ApiException(400, "Нужны данные поездки");
            }
            writeJson(resp, 201, rideService.start(userId, body.getNumberPlate(), body.getRateId(),
                    body.getPosX(), body.getPosY()));
        } else {
            writeJson(resp, 200, rideService.pay(userId, parseRideId(req, "pay")));
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (req.getPathInfo() != null && req.getPathInfo().endsWith("/review")) {
            String[] parts = req.getPathInfo().split("/", -1);
            if (parts.length != 3) throw new ApiException(404, "Путь не найден");
            var review = ru.itis.dto.ReviewRequest.from(ru.itis.util.Json.body(req));
            writeJson(resp, 200, rideService.review((UUID)req.getAttribute("userId"), parseUuid(parts[1], 404), review));
            return;
        }
        if(req.getPathInfo()!=null && req.getPathInfo().endsWith("/position")) {
            String[] parts=req.getPathInfo().split("/",-1);if(parts.length!=3)throw new ApiException(404,"Путь не найден");
            var body=ru.itis.util.Json.body(req);rideService.position((UUID)req.getAttribute("userId"),parseUuid(parts[1],404),ru.itis.util.Json.number(body,"posX",true),ru.itis.util.Json.number(body,"posY",true));resp.setStatus(204);return;
        }
        UUID id = parseRideId(req, "finish");
        FinishRideRequest body = objectMapper.treeToValue(ru.itis.util.Json.body(req), FinishRideRequest.class);
        if (body == null || body.getPosX() == null || body.getPosY() == null) {
            throw new ApiException(400, "Нужны posX и posY");
        }
        UUID userId = (UUID) req.getAttribute("userId");
        writeJson(resp, 200, rideService.finish(userId, id, body.getPosX(), body.getPosY()));
    }

    private UUID parseRideId(HttpServletRequest req, String operation) {
        String path = req.getPathInfo();
        if (path == null) {
            throw new ApiException(405, "Метод не поддерживается");
        }
        String[] parts = path.split("/", -1);
        if (parts.length != 3 || (!parts[2].equals("finish") && !parts[2].equals("pay"))) {
            throw new ApiException(404, "Путь не найден");
        }
        if (!parts[2].equals(operation)) {
            throw new ApiException(405, "Метод не поддерживается");
        }
        return parseUuid(parts[1], 404);
    }

    private UUID parseUuid(String value, int status) {
        if (value == null) {
            throw new ApiException(status, "Нужен UUID");
        }
        try {
            if(!value.matches("[0-9a-fA-F-]{36}"))throw new IllegalArgumentException();
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(status, "Некорректный UUID");
        }
    }

    private void writeJson(HttpServletResponse resp, int status, Object value) throws IOException {
        resp.setStatus(status);
        var tree=objectMapper.valueToTree(value);
        publicState(tree);
        objectMapper.writeValue(resp.getWriter(), tree);
    }
    private void publicState(com.fasterxml.jackson.databind.JsonNode n) {
        if(n.isArray()){n.forEach(this::publicState);return;}
        if(n.isObject() && "FINISHING".equals(n.path("status").asText()))
            ((com.fasterxml.jackson.databind.node.ObjectNode)n).put("status","ACTIVE");
    }

}
