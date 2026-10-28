package bff.filter;

import bff.service.ServiceException;
import bff.service.UserService;
import bff.service.impl.JsonText;
import bff.service.impl.UserServiceRestImpl;
import bff.web.Tokens;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebFilter("/api/*")
public class JwtAuthFilter implements Filter {

    private UserService userService;

    @Override
    public void init(FilterConfig filterConfig) {
        userService = new UserServiceRestImpl();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        if (bff.web.BrowserNavigation.reject(req, resp)) return;
        String path = req.getRequestURI().substring(req.getContextPath().length());

        if (("GET".equals(req.getMethod()) && "/api/reviews".equals(path)) || (("/api/auth/login".equals(path) || "/api/auth/register".equals(path)
                || "/api/auth/logout".equals(path)) && "POST".equals(req.getMethod()))
                || ("/api/users/exists".equals(path) && "GET".equals(req.getMethod()))) {
            chain.doFilter(request, response);
            return;
        }

        String jwt = Tokens.get(req);
        if (jwt == null || jwt.isBlank()) {
            writeError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Требуется авторизация");
            return;
        }

        try {
            if (!userService.validate(jwt)) {
                writeError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Недействительный или просроченный токен");
                return;
            }
        } catch (ServiceException e) {
            writeError(resp, e.status(), e.getMessage());
            return;
        }
        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write("{\"error\":" + JsonText.quote(message) + "}");
    }
}
