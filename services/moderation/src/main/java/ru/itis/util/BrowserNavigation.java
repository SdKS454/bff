package ru.itis.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;

/** Friendly browser navigation guard. API authorization remains a separate mandatory check. */
public final class BrowserNavigation {
    private BrowserNavigation() { }
    public static boolean reject(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String accept = req.getHeader("Accept");
        boolean navigation = "navigate".equalsIgnoreCase(req.getHeader("Sec-Fetch-Mode"))
                || (("GET".equals(req.getMethod()) || "HEAD".equals(req.getMethod()))
                && accept != null && accept.toLowerCase(Locale.ROOT).contains("text/html"));
        if (!navigation) return false;
        resp.setStatus(403); resp.setContentType("text/html;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store"); resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("Content-Security-Policy", "default-src 'none'; base-uri 'none'; frame-ancestors 'none'");
        String home = "http://localhost:8080/";
        resp.getWriter().write("<!doctype html><html lang=\"ru\"><meta charset=\"UTF-8\"><title>Служебный адрес</title>"
                + "<h1>Это служебный адрес</h1><p>Откройте сайт ScootX, чтобы пользоваться сервисом.</p>"
                + "<p><a href=\"" + home + "\">Перейти на сайт</a></p></html>");
        return true;
    }
}
