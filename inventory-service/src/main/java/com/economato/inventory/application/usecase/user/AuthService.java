package com.economato.inventory.application.usecase.user;

import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.economato.inventory.application.dto.shared.request.LoginRequestDTO;
import com.economato.inventory.application.dto.shared.response.LoginResponseDTO;
import com.economato.inventory.infrastructure.adapter.in.web.shared.exception.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.config.shared.security.JwtUtils;
import com.economato.inventory.application.mapper.user.UserMapper;

import java.util.Date;

@Service
public class AuthService {
    private final I18nService i18nService;

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthService(I18nService i18nService, UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtUtils jwtUtils,
            TokenBlacklistService tokenBlacklistService,
            UserMapper userMapper) {
        this.i18nService = i18nService;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    public LoginResponseDTO login(LoginRequestDTO loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getName(),
                        loginRequest.getPassword()));

        return jwtUtils.generateJwtToken(authentication);
    }

    public Optional<String> validateTokenAndGetUsername(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return Optional.ofNullable(authentication.getName());
        }
        return Optional.empty();
    }

    public Optional<String> getUserRoleString(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getAuthorities().stream()
                    .findFirst()
                    .map(authority -> authority.getAuthority().replace("ROLE_", ""));
        }
        return Optional.empty();
    }

    public Map<String, Object> validateToken(Authentication authentication) {
        return validateTokenAndGetUsername(authentication)
                .map(username -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("valid", true);
                    response.put("username", username);
                    return response;
                })
                .orElseThrow(() -> new BadCredentialsException(i18nService.getMessage(MessageKey.ERROR_AUTH_UNAUTHORIZED)));
    }

    public Map<String, String> getUserRole(Authentication authentication) {
        return getUserRoleString(authentication)
                .map(role -> {
                    Map<String, String> response = new HashMap<>();
                    response.put("role", role);
                    return response;
                })
                .orElseThrow(() -> new BadCredentialsException(i18nService.getMessage(MessageKey.ERROR_AUTH_UNAUTHORIZED)));
    }

    public Map<String, String> logoutWithHeader(String authHeader) {
        Map<String, String> response = new HashMap<>();

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_AUTH_LOGOUT_TOKEN_REQUIRED));
        }

        String token = authHeader.substring(7);

        logout(token);
        response.put("message", i18nService.getMessage(MessageKey.SUCCESS_AUTH_LOGOUT));
        return response;
    }

    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public void logout(String token) {
        if (token != null && !token.isEmpty()) {
            try {
                Date expirationDate = jwtUtils.getExpirationDateFromJwtToken(token);
                tokenBlacklistService.blacklistToken(token, expirationDate);
            } catch (Exception e) {

                throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_AUTH_INVALID_LOGOUT_TOKEN));
            }
        } else {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_AUTH_LOGOUT_TOKEN_REQUIRED));
        }
    }
}