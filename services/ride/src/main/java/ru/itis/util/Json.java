package ru.itis.util;
import ru.itis.exception.ApiException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
public final class Json {
 public static final ObjectMapper MAPPER=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
  .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
 public static JsonNode body(HttpServletRequest req)throws IOException {
  String type=req.getContentType();
  if(type==null||!type.split(";",2)[0].trim().equalsIgnoreCase("application/json"))throw new ApiException(415,"Ожидается application/json");
  byte[] bytes=req.getInputStream().readNBytes(65537);
  if(bytes.length>65536)throw new ApiException(413,"Слишком большое тело запроса");
  try{JsonNode n=MAPPER.readTree(bytes);if(n==null||!n.isObject())throw new IllegalArgumentException();return n;}
  catch(Exception e){throw new ApiException(400,"Некорректный JSON");}
 }
 public static String string(JsonNode n,String key){if(!n.path(key).isTextual()||n.get(key).textValue().isBlank())throw new ApiException(400,"Нужно поле "+key);return n.get(key).textValue();}
 public static UUID uuid(String s){try{if(s==null||!s.matches("[0-9a-fA-F-]{36}"))throw new IllegalArgumentException();return UUID.fromString(s);}catch(Exception e){throw new ApiException(400,"Нужен UUID");}}
 public static Double number(JsonNode n,String key,boolean required){var v=n.get(key);if(v==null||v.isNull()){if(required)throw new ApiException(400,"Нужно поле "+key);return null;}if(!v.isNumber()||!Double.isFinite(v.doubleValue())||Math.abs(v.doubleValue())>1_000_000)throw new ApiException(400,"Некорректное число "+key);return v.doubleValue();}
 public static void write(HttpServletResponse r,int status,Object value)throws IOException{r.setStatus(status);r.setContentType("application/json");r.setCharacterEncoding("UTF-8");MAPPER.writeValue(r.getWriter(),value);}
 public static void error(HttpServletResponse r,int status,String message)throws IOException{write(r,status,Map.of("error",message));}
}
