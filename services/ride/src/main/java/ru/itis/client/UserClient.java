package ru.itis.client;
import ru.itis.dto.UserResponse;
import ru.itis.exception.ApiException;
import ru.itis.util.Json;
import java.net.*;
import java.net.http.*;
import java.io.IOException;
import java.time.Duration;
public class UserClient {
 private final String base;
 private static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
 public UserClient(String base){this.base=base.replaceAll("/+$","");}
 public java.util.UUID identity(String token){
  if(request("/api/users/validate",token).statusCode()!=204)throw new ApiException(503,"Некорректный ответ проверки JWT");
  try {
   String[] parts=token.split("\\.",-1);
   if(parts.length!=3)throw new IllegalArgumentException();
   var payload=Json.MAPPER.readTree(java.util.Base64.getUrlDecoder().decode(parts[1]));
   return Json.uuid(payload.path("userId").asText());
  }catch(Exception e){throw new ApiException(401,"Некорректный JWT");}
 }
 public UserResponse current(String token){
  var id=identity(token);
  var response=request("/api/users/me",token);
  if(response.statusCode()!=200)throw new ApiException(502,"Некорректный профиль пользователя");
  try{
   var n=Json.MAPPER.readTree(response.body());
   if(n==null||!n.isObject()||!n.path("blocked").isBoolean()||!n.path("deleted").isBoolean())throw new IllegalArgumentException();
   Json.uuid(n.path("id").asText());
   var user=Json.MAPPER.treeToValue(n,UserResponse.class);
   if(!id.equals(user.getId()))throw new ApiException(502,"Некорректный профиль пользователя");
   if(user.isDeleted())throw new ApiException(401,"Пользователь удалён");return user;
  }catch(ApiException e){if(e.getStatus()==401)throw e;throw new ApiException(502,"Некорректный профиль пользователя");}
  catch(Exception e){throw new ApiException(502,"Некорректный профиль пользователя");}
 }
 private HttpResponse<String> request(String path,String token){
  try{
   var response=CLIENT.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(3)).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString());
   int code=response.statusCode();if(code==401||code==403)throw new ApiException(code,"User отклонил запрос");if(code>=500||code==429)throw new ApiException(503,"User недоступен");if(code!=200&&code!=204)throw new ApiException(502,"Некорректный ответ User");return response;
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new ApiException(503,"Запрос прерван");}
  catch(IOException e){throw new ApiException(503,"User недоступен");}
 }
}
