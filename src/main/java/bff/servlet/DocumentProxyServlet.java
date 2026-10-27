package bff.servlet;

import bff.dto.DocumentRequest;
import bff.service.DocumentService;
import bff.service.UserService;
import bff.service.impl.UserServiceRestImpl;
import java.time.LocalDate;
import java.util.UUID;
import bff.service.ServiceException;
import bff.service.impl.DocumentServiceRestImpl;
import bff.service.impl.JsonText;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/api/documents/*")
public class DocumentProxyServlet extends BaseServlet {

    private DocumentService documentService;
    private UserService userService;

    @Override
    public void init() throws ServletException {
        documentService = new DocumentServiceRestImpl();
        userService = new UserServiceRestImpl();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if(!"/generate".equals(req.getPathInfo())){writeError(resp,404,"Путь не найден");return;}
        String jwt = requireJwt(req, resp);
        if (jwt == null) {
            return;
        }

        req.setCharacterEncoding("UTF-8");
        try {
            DocumentRequest body = readDocument(req, jwt);
            byte[] pdf = documentService.generate(jwt, body);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("application/pdf");
            resp.setHeader("Cache-Control", "no-store");
            resp.setHeader("X-Content-Type-Options", "nosniff");
            resp.setHeader("Content-Disposition", "attachment; filename=\"document.pdf\"");
            resp.getOutputStream().write(pdf);
        } catch (ServiceException e) {
            writeServiceError(resp, e);
        } catch (IllegalArgumentException e) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    e.getMessage() != null ? e.getMessage() : "Некорректный запрос");
        }
    }

    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        proxyRead(req,resp,false);
    }
    @Override protected void doDelete(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        proxyRead(req,resp,true);
    }
    private void proxyRead(HttpServletRequest req,HttpServletResponse resp,boolean delete)throws IOException {
        String jwt=requireJwt(req,resp);if(jwt==null)return;
        try {
            String path=req.getPathInfo()==null?"":req.getPathInfo();
            if(path.equals("/generate")){writeError(resp,405,"Метод не поддерживается");return;}
            if(path.isEmpty()||path.equals("/")) {
                if(delete){writeError(resp,405,"Метод не поддерживается");return;}
                path="?limit="+page(req,"limit",50,1,100)+"&offset="+page(req,"offset",0,0,Integer.MAX_VALUE);
            }else {
                String[] parts=path.substring(1).split("/",-1);
                if(parts.length<1||parts.length>2||!parts[0].matches("[0-9a-fA-F-]{36}"))throw new ServiceException(404,"Неизвестный путь");
                UUID id;try{id=UUID.fromString(parts[0]);}catch(IllegalArgumentException e){throw new ServiceException(404,"Неизвестный путь");}
                if(parts.length==2) {
                    if(delete||!parts[1].equals("download"))throw new ServiceException(404,"Неизвестный путь");
                    byte[] pdf=documentService.download(jwt,id);resp.setStatus(200);resp.setContentType("application/pdf");resp.setHeader("Content-Disposition","attachment; filename=\"document.pdf\"");resp.getOutputStream().write(pdf);return;
                }
                path="/"+id;
            }
            var result=documentService.metadata(jwt,delete?"DELETE":"GET",path);
            if(result.status()==204)resp.setStatus(204);else writeJson(resp,result.status(),result.body());
        }catch(ServiceException e){writeServiceError(resp,e);}
        catch(IllegalArgumentException e){writeError(resp,400,e.getMessage());}
    }
    private int page(HttpServletRequest req,String name,int fallback,int min,int max) {
        String value=req.getParameter(name);if(value==null)return fallback;
        try{if(!value.matches("[0-9]{1,10}"))throw new IllegalArgumentException();int n=Integer.parseInt(value);if(n<min||n>max)throw new IllegalArgumentException();return n;}
        catch(IllegalArgumentException e){throw new IllegalArgumentException("Некорректный "+name);}
    }

    private DocumentRequest readDocument(HttpServletRequest req, String jwt) throws IOException {
        String json = JsonText.read(req.getInputStream());
        String type = JsonText.requiredString(json, "docType");
        // Compatibility with the old BFF client; downstream uses the agreed document type.
        if ("RECEIPT".equals(type)) type = "RIDE_PAYMENT";
        if (!java.util.Set.of("RIDE_PAYMENT", "USER_BLOCKED", "USER_HISTORY").contains(type))
            throw new IllegalArgumentException("Неизвестный docType");
        UUID sourceId = "RIDE_PAYMENT".equals(type) ? JsonText.uuidOrNull(json, "sourceId") : null;
        if ("RIDE_PAYMENT".equals(type) && sourceId == null) throw new IllegalArgumentException("Нужен sourceId поездки");
        String from = null, to = null;
        if ("USER_HISTORY".equals(type)) {
            from = JsonText.requiredString(json, "from");
            to = JsonText.requiredString(json, "to");
            try {
                if (LocalDate.parse(from).isAfter(LocalDate.parse(to))) throw new IllegalArgumentException("Некорректный период");
            } catch (java.time.DateTimeException e) { throw new IllegalArgumentException("Некорректная дата", e); }
        }
        var me = userService.currentUser(jwt);
        if (me.deleted()) throw new ServiceException(403, "Пользователь удалён");
        return new DocumentRequest(me.id(), type, sourceId, from, to);
    }
}
