package ru.itis.servlet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import ru.itis.service.RideIntegrationService;
import ru.itis.util.Json;
import ru.itis.exception.ApiException;
import java.io.IOException;
@WebServlet("/api/rates")
public class RateServlet extends HttpServlet {
 @Override protected void service(HttpServletRequest req,HttpServletResponse resp)throws IOException {
  if(!req.getMethod().equals("GET"))throw new ApiException(405,"Метод не поддерживается");
  Json.write(resp,200,new RideIntegrationService().rates());
 }
}
