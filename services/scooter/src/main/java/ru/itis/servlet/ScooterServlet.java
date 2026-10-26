package ru.itis.servlet;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import ru.itis.repository.Scooters;
import ru.itis.util.*;
import java.io.IOException;
import java.util.*;

@WebServlet("/api/scooters/*")
public class ScooterServlet extends HttpServlet {
 private final Scooters scooters=new Scooters();
 @Override protected void service(HttpServletRequest req,HttpServletResponse resp)throws IOException {
  String path=req.getPathInfo();String method=req.getMethod();
  if((path==null||path.equals("/"))&&method.equals("GET")){
   String status=req.getParameter("status");if(status!=null&&!Set.of("READY","IN_RIDE","CHARGING","REPAIR").contains(status))throw new ApiException(400,"Некорректный status");
   Json.write(resp,200,scooters.list(status,null,null));return;
  }
  if("/nearest".equals(path)){
   require(method,"POST");var body=Json.body(req);Json.write(resp,200,scooters.list("READY",Json.number(body,"posX",true),Json.number(body,"posY",true)));return;
  }
  String[] parts=path==null?new String[0]:path.substring(1).split("/",-1);
  if(parts.length==1&&!parts[0].isBlank()){require(method,"GET");Json.write(resp,200,scooters.get(parts[0]));return;}
  if(parts.length==2&&Set.of("reserve","release").contains(parts[1])){
   require(method,"POST");var body=Json.body(req);var ride=Json.uuid(Json.string(body,"rideId"));
   if(parts[1].equals("reserve"))Json.write(resp,200,scooters.reserve(parts[0],ride,Json.uuid(Json.string(body,"userId"))));
   else {Double x=Json.number(body,"posX",false),y=Json.number(body,"posY",false);if((x==null)!=(y==null))throw new ApiException(400,"Нужны обе координаты");Json.write(resp,200,scooters.release(parts[0],ride,x,y));}return;
  }
  throw new ApiException(404,"Путь не найден");
 }
 private void require(String actual,String expected){if(!actual.equals(expected))throw new ApiException(405,"Метод не поддерживается");}
}
