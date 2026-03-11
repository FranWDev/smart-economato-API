package com.economato.inventory.application.usecase;

import com.economato.inventory.infrastructure.config.web.I18nService;
import com.economato.inventory.infrastructure.config.web.MessageKey;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.application.dto.request.LoginRequestDTO;
import com.economato.inventory.application.dto.response.LoginResponseDTO;
import com.economato.inventory.infrastructure.adapter.in.web.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.UserRepository;
import com.economato.inventory.infrastructure.config.security.JwtUtils;
import com.economato.inventory.application.mapper.UserMapper;

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