package ru.itis.security;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.itis.exception.ApiException;
import ru.itis.util.Util;

import java.io.IOException;
import java.util.logging.Logger;

@WebFilter("/api/moderation/*")
public class AuthFilter implements Filter {

    public static final String ATTR_TOKEN = "token";
    public static final String ATTR_ROLE = "role";
    public static final String ATTR_USER_ID = "userId";
    private static final Logger LOG = Logger.getLogger(AuthFilter.class.getName());
    private final java.util.function.Consumer<String> validateToken;

    public AuthFilter() {
        this(new UserTokenValidator()::validate);
    }


    AuthFilter(java.util.function.Consumer<String> validateToken) {
        this.validateToken = validateToken;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        if (ru.itis.util.BrowserNavigation.reject(req, resp)) return;
        String method = req.getMethod();
        String path = req.getServletPath() + (req.getPathInfo() == null ? "" : req.getPathInfo());

        try {
            checkToken(req, method, path);
        } catch (ApiException e) {
            LOG.warning("Отказ " + e.getStatus() + ": " + method + " " + path);
            Util.sendError(resp, e.getStatus(), e.getMessage());
            return;
        }
        chain.doFilter(req, resp);
    }

    private void checkToken(HttpServletRequest req, String method, String path) {
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ApiException(401, "Требуется авторизация");
        }
        String token = header.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw new ApiException(401, "Требуется авторизация");
        }

        JsonNode payload = JwtUtil.readPayload(token);
        if (payload == null || !payload.hasNonNull("userId")) {
            throw new ApiException(401, "Требуется авторизация");
        }
        if (payload.hasNonNull("exp") && payload.get("exp").asLong() < System.currentTimeMillis() / 1000) {
            throw new ApiException(401, "Требуется авторизация");
        }



        validateToken.accept(token);

        String role = payload.hasNonNull("role") ? payload.get("role").asText() : null;
        String userId = payload.get("userId").asText();

        if (!isStatusPath(method, path) && !"ADMIN".equals(role)) {
            throw new ApiException(403, "Недостаточно прав");
        }

        req.setAttribute(ATTR_TOKEN, token);
        req.setAttribute(ATTR_ROLE, role);
        req.setAttribute(ATTR_USER_ID, userId);
    }

    private boolean isStatusPath(String method, String path) {
        return method.equals("GET") && path.matches("/api/moderation/users/[^/]+/status");
    }
}
