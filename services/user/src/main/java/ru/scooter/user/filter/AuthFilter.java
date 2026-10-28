package ru.scooter.user.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import ru.scooter.user.config.AppContextListener;
import ru.scooter.user.dto.AuthPrincipal;
import ru.scooter.user.exception.ApiException;
import ru.scooter.user.service.AuthService;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

public final class AuthFilter implements Filter {
    public static final String PRINCIPAL = AuthPrincipal.class.getName();
    private static final Set<String> PUBLIC_POSTS =
            Set.of("/api/users/register", "/api/users/login");
    private AuthService auth;

    public static AuthPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(PRINCIPAL);
        if (!(value instanceof AuthPrincipal principal)) throw unauthorized();
        return principal;
    }

    private static ApiException unauthorized() {
        return new ApiException(401, "Требуется Authorization: Bearer <JWT>");
    }

    @Override
    public void init(FilterConfig config) {
        auth = AppContextListener.components(config.getServletContext()).auth();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String path = req.getRequestURI().substring(req.getContextPath().length());
        boolean isPublic =
                ("POST".equals(req.getMethod()) && PUBLIC_POSTS.contains(path))
                        || ("GET".equals(req.getMethod()) && "/api/users/exists".equals(path));
        if (!isPublic) {
            java.util.List<String> headers = Collections.list(req.getHeaders("Authorization"));
            if (headers.size() != 1) throw unauthorized();
            String header = headers.get(0);
            if (header.length() < 8 || !header.regionMatches(true, 0, "Bearer ", 0, 7))
                throw unauthorized();
            String token = header.substring(7);
            if (token.isBlank() || token.chars().anyMatch(Character::isWhitespace))
                throw unauthorized();
            req.setAttribute(PRINCIPAL, auth.authenticate(token));
        }
        chain.doFilter(request, response);
    }
}
