package ru.itis.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.itis.client.UserClient;
import ru.itis.exception.ApiException;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@WebFilter("/*")
public class AuthFilter implements Filter {
    private UserClient userClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void init(FilterConfig config) {
        String url = System.getenv("USER_SERVICE_URL");
        userClient = new UserClient(url == null ? "http://localhost:8081" : url);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        if (ru.itis.util.BrowserNavigation.reject(req, resp)) return;
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        try {
            String publicPath = req.getRequestURI().substring(req.getContextPath().length());
            if (req.getMethod().equals("GET") && publicPath.equals("/api/reviews")) {
                chain.doFilter(req, resp); return;
            }
            String authorization = req.getHeader("Authorization");
            if (authorization == null || !authorization.regionMatches(true,0,"Bearer ",0,7)) {
                throw new ApiException(401, "Нужен Bearer JWT");
            }
            String token = authorization.substring(7);
            if (token.isBlank()) {
                throw new ApiException(401, "Нужен Bearer JWT");
            }
            var userId = userClient.identity(token);
            req.setAttribute("userId", userId);
            String path=req.getRequestURI().substring(req.getContextPath().length());
            if(req.getMethod().equals("POST") && (path.equals("/api/rides")||path.equals("/api/rides/"))) {
                var user=userClient.current(token);
                req.setAttribute("blocked",user.isBlocked());
            }
            chain.doFilter(req, resp);
        } catch (ApiException e) {
            resp.setStatus(e.getStatus());
            if (e.getStatus() == 401) {
                resp.setHeader("WWW-Authenticate", "Bearer");
            }
            objectMapper.writeValue(resp.getWriter(), Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            resp.setStatus(500);
            objectMapper.writeValue(resp.getWriter(), Map.of("error", "Внутренняя ошибка сервиса"));
        }
    }
}
