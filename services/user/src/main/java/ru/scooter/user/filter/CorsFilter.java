package ru.scooter.user.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.scooter.user.config.AppContextListener;
import ru.scooter.user.exception.ApiException;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

public final class CorsFilter implements Filter {
    private static final Set<String> METHODS = Set.of("GET", "POST", "OPTIONS");
    private static final Set<String> HEADERS = Set.of("authorization", "content-type", "accept");
    private Set<String> allowedOrigins;

    @Override
    public void init(FilterConfig config) {
        allowedOrigins =
                AppContextListener.components(config.getServletContext()).config().allowedOrigins();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        resp.addHeader("Vary", "Origin");
        String origin = req.getHeader("Origin");
        boolean preflight =
                "OPTIONS".equals(req.getMethod())
                        && origin != null
                        && req.getHeader("Access-Control-Request-Method") != null;
        if (origin != null) {
            String ownOrigin =
                    req.getScheme()
                            + "://"
                            + req.getServerName()
                            + ((req.getServerPort() == 80 && "http".equals(req.getScheme())
                            || req.getServerPort() == 443
                            && "https".equals(req.getScheme()))
                            ? ""
                            : ":" + req.getServerPort());
            if (!allowedOrigins.contains(origin) && !ownOrigin.equals(origin))
                throw new ApiException(403, "Origin не разрешён");
            resp.setHeader("Access-Control-Allow-Origin", origin);
            resp.setHeader("Access-Control-Expose-Headers", "WWW-Authenticate");
        }
        if (preflight) {
            resp.addHeader("Vary", "Access-Control-Request-Method");
            resp.addHeader("Vary", "Access-Control-Request-Headers");
            if (!METHODS.contains(req.getHeader("Access-Control-Request-Method")))
                throw new ApiException(403, "Метод CORS не разрешён");
            String requested = req.getHeader("Access-Control-Request-Headers");
            if (requested != null)
                for (String header : requested.split(",")) {
                    if (!HEADERS.contains(header.trim().toLowerCase(Locale.ROOT)))
                        throw new ApiException(403, "Заголовок CORS не разрешён");
                }
            resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            resp.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept");
            resp.setHeader("Access-Control-Max-Age", "600");
        }
        if ("OPTIONS".equals(req.getMethod())) {
            resp.setStatus(204);
            return;
        }
        chain.doFilter(request, response);
    }
}
