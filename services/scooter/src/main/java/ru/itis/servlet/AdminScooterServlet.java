package ru.itis.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.itis.repository.Scooters;
import ru.itis.util.ApiException;
import ru.itis.util.Json;

import java.io.IOException;




@WebServlet("/api/admin/scooters/*")
public final class AdminScooterServlet extends HttpServlet {
    private final Scooters scooters = new Scooters();

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo(), method = req.getMethod();
        if (path == null || path.equals("/")) {
            if (method.equals("GET")) {
                Json.write(resp, 200, scooters.list(null, null, null));
                return;
            }
            if (method.equals("POST")) {
                var body = Json.body(req);
                Json.write(resp, 201, scooters.create(Json.string(body, "numberPlate"), Json.number(body, "posX", true), Json.number(body, "posY", true)));
                return;
            }
            throw new ApiException(405, "Используйте GET или POST");
        }
        String[] parts = path.substring(1).split("/", -1);
        if (parts.length == 1 && parts[0].matches("[A-Za-z0-9_.-]{1,64}")) {
            if (!method.equals("DELETE")) throw new ApiException(405, "Используйте DELETE");
            scooters.delete(parts[0]);
            resp.setStatus(204);
            return;
        }
        if (parts.length == 2 && parts[0].matches("[A-Za-z0-9_.-]{1,64}") && parts[1].equals("charge")) {
            if (!method.equals("POST")) throw new ApiException(405, "Используйте POST");
            Json.write(resp, 200, scooters.charge(parts[0]));
            return;
        }
        throw new ApiException(404, "Путь не найден");
    }
}
