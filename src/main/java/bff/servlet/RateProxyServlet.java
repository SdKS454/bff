package bff.servlet;

import bff.service.ServiceException;
import bff.service.impl.RideServiceRestImpl;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/api/rates")
public class RateProxyServlet extends BaseServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String jwt = requireJwt(req, resp);
        if (jwt == null) return;
        try {
            var result = new RideServiceRestImpl().rates(jwt);
            writeJson(resp, result.status(), result.body());
        } catch (ServiceException e) {
            writeServiceError(resp, e);
        }
    }
}
