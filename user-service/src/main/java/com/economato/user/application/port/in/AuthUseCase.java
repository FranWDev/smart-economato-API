package com.economato.user.application.port.in;

import com.economato.user.application.dto.request.LoginRequestDTO;
import com.economato.user.application.dto.response.LoginResponseDTO;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Optional;

public interface AuthUseCase {
    LoginResponseDTO login(LoginRequestDTO loginRequest);
    Map<String, Object> validateToken(Authentication authentication);
    Map<String, String> getUserRole(Authentication authentication);
    Map<String, String> logoutWithHeader(String authHeader);
    void logout(String token);
    Optional<String> validateTokenAndGetUsername(Authentication authentication);
    Optional<String> getUserRoleString(Authentication authentication);
}
