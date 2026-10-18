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

@WebServlet("/api/documents/generate")
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
