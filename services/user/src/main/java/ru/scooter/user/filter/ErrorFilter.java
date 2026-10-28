package ru.scooter.user.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.scooter.user.exception.ApiException;
import ru.scooter.user.exception.DataAccessException;
import ru.scooter.user.util.Json;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ErrorFilter implements Filter {
    private static final Logger LOG = Logger.getLogger(ErrorFilter.class.getName());

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse resp = (HttpServletResponse) response;
        if (ru.scooter.user.util.BrowserNavigation.reject((HttpServletRequest) request, resp)) return;
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("Referrer-Policy", "no-referrer");
        resp.setHeader(
                "Content-Security-Policy",
                "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self';"
                        + " form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
        resp.setHeader("Cache-Control", "no-store");
        request.setCharacterEncoding("UTF-8");
        try {
            chain.doFilter(request, response);
        } catch (ApiException e) {
            if (e.status() >= 500)
                LOG.log(Level.WARNING, "Request failed with status " + e.status());
            if (!resp.isCommitted()) Json.error(resp, e.status(), e.getMessage());
        } catch (DataAccessException e) {
            LOG.log(Level.WARNING, "Database request failed");
            if (!resp.isCommitted()) Json.error(resp, 503, "База данных недоступна");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unhandled request failure", e);
            if (!resp.isCommitted()) Json.error(resp, 500, "Внутренняя ошибка сервиса");
        }
    }
}
