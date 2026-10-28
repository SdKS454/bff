package ru.itis.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.itis.util.Util;

import java.io.IOException;
import java.net.URI;




@WebServlet("/admin/js/config.js")
public class AdminConfigServlet extends HttpServlet {
    private static String url(String name, String fallback) {
        String value = System.getenv().getOrDefault(name, fallback);
        URI uri = URI.create(value);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException(name + " must be an HTTP(S) URL without query, credentials or fragment");
        return uri.toASCIIString();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String user = url("ADMIN_USER_BASE_URL", System.getenv().getOrDefault("USER_BASE_URL", "http://localhost:8081"));
        String login = url("BFF_LOGIN_URL", "http://localhost:8080/login.jsp");
        resp.setContentType("application/javascript");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write("const USER_URL = " + Util.MAPPER.writeValueAsString(user.replaceAll("/+$", ""))
                + ";\nconst LOGIN_URL = " + Util.MAPPER.writeValueAsString(login) + ";\n");
    }
}
