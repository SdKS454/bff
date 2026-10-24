package ru.kicksharing.documents.client;

import ru.kicksharing.documents.dto.external.UserResponse;
import ru.kicksharing.documents.exception.ApiException;

public class UserClient {
    public void validate(String token) {
        // /validate never calls Moderation. It verifies role, user state and DB revocation.
        int status = ServiceHttp.get("user.service.url", "/api/users/validate", token).statusCode();
        if (status == 204) return;
        if (status == 401) throw new ApiException(401, "Недействительный JWT");
        throw ApiException.serviceUnavailable("Сервис авторизации временно недоступен");
    }

    public UserResponse getUser(String userId, String token) {
        var value = ServiceHttp.json("user.service.url", "/api/users/" + userId, token);
        UserResponse user = ServiceHttp.object(value, UserResponse.class);
        if (!ServiceHttp.sameId(user.getId(), userId) || user.getLogin() == null || user.getLogin().isBlank()
                || user.getFullName() == null || user.getFullName().isBlank()) throw ServiceHttp.badResponse();
        return user;
    }
}
