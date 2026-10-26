package ru.itis.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.itis.dao.impl.BlockDaoImpl;
import ru.itis.dao.impl.ReasonDaoImpl;
import ru.itis.dto.BanRequest;
import ru.itis.dto.ReasonResponse;
import ru.itis.dto.StatusResponse;
import ru.itis.entity.BlockStatus;
import ru.itis.entity.Reason;
import ru.itis.exception.ApiException;
import ru.itis.exception.ValidationException;
import ru.itis.security.AuthFilter;
import ru.itis.service.ModerationService;
import ru.itis.service.impl.ModerationServiceImpl;
import ru.itis.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/api/moderation/*")
public class ModerationServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(ModerationServlet.class.getName());

    private ModerationService moderationService;

    @Override
    public void init() {
        moderationService = new ModerationServiceImpl(new BlockDaoImpl(), new ReasonDaoImpl());
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            route(req, resp);
        } catch (ApiException e) {
            Util.sendError(resp, e.getStatus(), e.getMessage());
        } catch (ValidationException e) {
            Util.sendError(resp, 400, e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Необработанная ошибка", e);
            Util.sendError(resp, 500, "Внутренняя ошибка");
        }
    }

    private void route(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        String[] parts = path.isEmpty() ? new String[0] : path.substring(1).split("/", -1);

        if (parts.length == 3 && parts[0].equals("users") && parts[2].equals("status")) {
            requireMethod(req, "GET");
            getStatus(parts[1], req, resp);
        } else if (parts.length == 1 && parts[0].equals("reasons")) {
            requireMethod(req, "GET");
            getReasons(resp);
        } else if (parts.length == 1 && parts[0].equals("users")) {
            requireMethod(req, "GET");
            getUsers(req, resp);
        } else if (parts.length == 3 && parts[0].equals("users") && parts[2].equals("ban")) {
            requireMethod(req, "POST");
            ban(parts[1], req, resp);
        } else if (parts.length == 3 && parts[0].equals("users") && parts[2].equals("unban")) {
            requireMethod(req, "POST");
            unban(parts[1], req, resp);
        } else {
            throw new ApiException(404, "Не найдено");
        }
    }

    private void requireMethod(HttpServletRequest req, String method) {
        if (!req.getMethod().equals(method)) {
            throw new ApiException(405, "Метод не поддерживается");
        }
    }

    private void getStatus(String userIdText, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UUID userId = Util.parseUuid(userIdText);

        String role = (String) req.getAttribute(AuthFilter.ATTR_ROLE);
        String tokenUserId = (String) req.getAttribute(AuthFilter.ATTR_USER_ID);
        boolean admin = "ADMIN".equals(role);
        boolean self = userId.toString().equalsIgnoreCase(tokenUserId);
        if (!admin && !self) {
            throw new ApiException(403, "Недостаточно прав");
        }

        Util.sendJson(resp, 200, toResponse(moderationService.getStatus(userId)));
    }

    private void getReasons(HttpServletResponse resp) throws IOException {
        List<ReasonResponse> result = new ArrayList<>();
        for (Reason reason : moderationService.getReasons()) {
            result.add(new ReasonResponse(
                    reason.getReasonType().name(),
                    reason.getDescription(),
                    reason.getLockTime()));
        }
        Util.sendJson(resp, 200, result);
    }

    private void getUsers(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String blocked = req.getParameter("blocked");
        if (blocked != null && !blocked.equals("true")) {
            throw new ApiException(400, "Поддерживается только blocked=true");
        }

        int limit = Util.parseInt(req.getParameter("limit"), 50);
        int offset = Util.parseInt(req.getParameter("offset"), 0);

        List<StatusResponse> result = new ArrayList<>();
        for (BlockStatus status : moderationService.listBlocked(limit, offset)) {
            result.add(toResponse(status));
        }
        Util.sendJson(resp, 200, result);
    }

    private void ban(String userIdText, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UUID userId = Util.parseUuid(userIdText);

        BanRequest body = Util.readBody(req, BanRequest.class);
        if (body == null || body.getReason() == null || body.getReason().isBlank()) {
            throw new ApiException(400, "Не указана причина блокировки");
        }

        String adminId = (String) req.getAttribute(AuthFilter.ATTR_USER_ID);

        BlockStatus status = moderationService.ban(userId, body.getReason());
        LOG.info("Блокировка: adminId=" + adminId + ", userId=" + userId + ", причина=" + body.getReason());

        Util.sendJson(resp, 200, toResponse(status));
    }

    private void unban(String userIdText, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UUID userId = Util.parseUuid(userIdText);
        String adminId = (String) req.getAttribute(AuthFilter.ATTR_USER_ID);

        BlockStatus status = moderationService.unban(userId);
        LOG.info("Разблокировка: adminId=" + adminId + ", userId=" + userId);

        Util.sendJson(resp, 200, toResponse(status));
    }

    private StatusResponse toResponse(BlockStatus status) {
        return new StatusResponse(
                status.getUserId(),
                status.isBlocked(),
                status.getReasonType(),
                status.getReason(),
                Util.formatDate(status.getBlockedAt()),
                Util.formatDate(status.getBlockedUntil()));
    }
}
