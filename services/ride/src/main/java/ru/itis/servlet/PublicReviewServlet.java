package ru.itis.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import ru.itis.exception.ApiException;
import ru.itis.repository.impl.PublicReviewRepository;
import ru.itis.util.Json;
import java.io.IOException;

@WebServlet("/api/reviews")
public final class PublicReviewServlet extends HttpServlet {
    private final PublicReviewRepository repository = new PublicReviewRepository();
    @Override protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!req.getMethod().equals("GET")) throw new ApiException(405, "Используйте GET");
        Json.write(resp, 200, repository.list(page(req, "limit", 20, 1, 100), page(req, "offset", 0, 0, Integer.MAX_VALUE)));
    }
    private static int page(HttpServletRequest req, String key, int fallback, int min, int max) {
        String text = req.getParameter(key); if (text == null) return fallback;
        try {
            if (!text.matches("[0-9]{1,10}")) throw new IllegalArgumentException();
            int value = Integer.parseInt(text);
            if (value < min || value > max) throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException e) { throw new ApiException(400, "Некорректный " + key); }
    }
}
