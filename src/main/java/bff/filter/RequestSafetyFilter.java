package bff.filter;

import bff.service.impl.JsonText;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.util.Set;

/** Reject browser cross-site writes; API writes carrying a body must use JSON. */
@WebFilter("/*")
public class RequestSafetyFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var req = (HttpServletRequest) request;
        var resp = (HttpServletResponse) response;
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("Referrer-Policy", "same-origin");
        resp.setHeader("Cache-Control", "no-store");
        if (Set.of("POST", "PUT", "PATCH", "DELETE").contains(req.getMethod())) {
            String origin = req.getHeader("Origin");
            String ownOrigin = URI.create(req.getRequestURL().toString()).resolve("/").toString();
            if ("cross-site".equals(req.getHeader("Sec-Fetch-Site"))
                    || (origin != null && !sameOrigin(origin, ownOrigin))) {
                error(resp, 403, "Межсайтовые запросы запрещены");
                return;
            }
            String path = req.getRequestURI().substring(req.getContextPath().length());
            if (path.startsWith("/api/") && (req.getContentLengthLong() > 0 || req.getHeader("Transfer-Encoding") != null)) {
                String type = req.getContentType();
                if (type == null || !"application/json".equalsIgnoreCase(type.split(";", 2)[0].trim())) {
                    error(resp, 415, "Ожидается Content-Type: application/json");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }
    private boolean sameOrigin(String origin, String ownOrigin) {
        try {
            URI a = URI.create(origin), b = URI.create(ownOrigin);
            return a.getHost() != null && a.getHost().equalsIgnoreCase(b.getHost())
                    && a.getScheme().equalsIgnoreCase(b.getScheme()) && port(a) == port(b)
                    && (a.getRawPath() == null || a.getRawPath().isEmpty())
                    && a.getRawQuery() == null && a.getRawFragment() == null && a.getRawUserInfo() == null;
        } catch (IllegalArgumentException e) { return false; }
    }
    private int port(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
    private void error(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write("{\"error\":" + JsonText.quote(message) + "}");
    }
}
