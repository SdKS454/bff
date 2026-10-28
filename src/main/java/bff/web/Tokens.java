package bff.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public final class Tokens {
    private Tokens() {
    }

    public static String get(HttpServletRequest req) {
        String header = req.getHeader("Authorization");

        if (header != null) {
            if (!header.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
            String token = header.substring(7).trim();
            return token.isEmpty() || token.chars().anyMatch(Character::isWhitespace) ? null : token;
        }
        HttpSession session = req.getSession(false);
        return session != null && session.getAttribute("jwt") instanceof String token && !token.isBlank() ? token : null;
    }
}
