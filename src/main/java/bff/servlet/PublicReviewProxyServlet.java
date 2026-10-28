package bff.servlet;

import bff.service.ServiceException;
import bff.service.impl.RideServiceRestImpl;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet("/api/reviews")
public final class PublicReviewProxyServlet extends BaseServlet {
    private final RideServiceRestImpl rides = new RideServiceRestImpl();
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            var result = rides.reviews(page(req, "limit", 20, 1, 100), page(req, "offset", 0, 0, Integer.MAX_VALUE));
            writeJson(resp, result.status(), result.body());
        } catch (ServiceException e) { writeServiceError(resp, e); }
        catch (IllegalArgumentException e) { writeError(resp, 400, e.getMessage()); }
    }
    private static int page(HttpServletRequest req, String key, int fallback, int min, int max) {
        String text = req.getParameter(key); if (text == null) return fallback;
        try {
            if (!text.matches("[0-9]{1,10}")) throw new IllegalArgumentException();
            int value = Integer.parseInt(text);
            if (value < min || value > max) throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Некорректный " + key); }
    }
}
