package bff.service;

import java.util.UUID;

import bff.dto.AuthRequest;
import bff.dto.AuthResponse;
import bff.dto.JsonResponse;
import bff.dto.UserDto;

public interface UserService {

    JsonResponse register(AuthRequest request);

    AuthResponse login(AuthRequest request);

    JsonResponse profile(String jwt);

    UserDto currentUser(String jwt);

    JsonResponse exists(String login);

    boolean validate(String jwt);

    boolean isBlocked(UUID userId, String jwt);
}
