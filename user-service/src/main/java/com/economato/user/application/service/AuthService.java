package com.economato.user.application.service;

import com.economato.user.application.dto.request.LoginRequestDTO;
import com.economato.user.application.dto.response.LoginResponseDTO;
import com.economato.user.application.port.in.AuthUseCase;
import com.economato.user.application.port.out.TokenBlacklistPort;
import com.economato.user.infrastructure.config.security.JwtUtils;
import com.economato.user.infrastructure.config.web.I18nService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService implements AuthUseCase {

    private final I18nService i18nService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final TokenBlacklistPort tokenBlacklistPort;

    public AuthService(I18nService i18nService,
                       AuthenticationManager authenticationManager,
                       JwtUtils jwtUtils,
                       TokenBlacklistPort tokenBlacklistPort) {
        this.i18nService = i18nService;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.tokenBlacklistPort = tokenBlacklistPort;
    }

    @Override
    public LoginResponseDTO login(LoginRequestDTO loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getName(),
                        loginRequest.getPassword()
                )
        );

        return jwtUtils.generateJwtToken(authentication);
    }

    @Override
    public Optional<String> validateTokenAndGetUsername(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return Optional.ofNullable(authentication.getName());
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> getUserRoleString(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getAuthorities().stream()
                    .findFirst()
                    .map(authority -> authority.getAuthority().replace("ROLE_", ""));
        }
        return Optional.empty();
    }

    @Override
    public Map<String, Object> validateToken(Authentication authentication) {
        return validateTokenAndGetUsername(authentication)
                .map(username -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("valid", true);
                    response.put("username", username);
                    return response;
                })
                .orElseThrow(() -> new BadCredentialsException(i18nService.getMessage("error.auth.unauthorized")));
    }

    @Override
    public Map<String, String> getUserRole(Authentication authentication) {
        return getUserRoleString(authentication)
                .map(role -> {
                    Map<String, String> response = new HashMap<>();
                    response.put("role", role);
                    return response;
                })
                .orElseThrow(() -> new BadCredentialsException(i18nService.getMessage("error.auth.unauthorized")));
    }

    @Override
    public Map<String, String> logoutWithHeader(String authHeader) {
        Map<String, String> response = new HashMap<>();

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException(i18nService.getMessage("error.auth.logout.token.required"));
        }

        String token = authHeader.substring(7);
        logout(token);
        response.put("message", i18nService.getMessage("success.auth.logout"));
        return response;
    }

    @Override
    public void logout(String token) {
        if (token != null && !token.isEmpty()) {
            try {
                Date expirationDate = jwtUtils.getExpirationDateFromJwtToken(token);
                tokenBlacklistPort.blacklistToken(token, expirationDate);
            } catch (Exception e) {
                throw new IllegalArgumentException(i18nService.getMessage("error.auth.invalid.logout.token"));
            }
        } else {
            throw new IllegalArgumentException(i18nService.getMessage("error.auth.logout.token.required"));
        }
    }
}
