package com.economato.inventory.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.economato.inventory.dto.request.LoginRequestDTO;
import com.economato.inventory.dto.request.UserRequestDTO;
import com.economato.inventory.dto.response.LoginResponseDTO;
import com.economato.inventory.dto.response.UserResponseDTO;
import com.economato.inventory.exception.InvalidOperationException;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.UserRepository;
import com.economato.inventory.security.JwtUtils;

import java.util.Date;

@Service
@Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;
    private final com.economato.inventory.mapper.UserMapper userMapper;

    public AuthService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtUtils jwtUtils,
            TokenBlacklistService tokenBlacklistService,
            com.economato.inventory.mapper.UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.tokenBlacklistService = tokenBlacklistService;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public LoginResponseDTO login(LoginRequestDTO loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getName(),
                        loginRequest.getPassword()));

        return jwtUtils.generateJwtToken(authentication);
    }

    @Transactional(rollbackFor = { InvalidOperationException.class, RuntimeException.class, Exception.class })
    public UserResponseDTO register(UserRequestDTO requestDTO) {
        if (userRepository.existsByUser(requestDTO.getUser())) {
            throw new InvalidOperationException("User already exists");
        }

        User user = userMapper.toEntity(requestDTO);
        user.setPassword(passwordEncoder.encode(requestDTO.getPassword()));
        user.setFirstLogin(true);

        User savedUser = userRepository.save(user);

        return userMapper.toResponseDTO(savedUser);
    }

    public void logout(String token) {
        if (token != null && !token.isEmpty()) {
            try {
                Date expirationDate = jwtUtils.getExpirationDateFromJwtToken(token);
                tokenBlacklistService.blacklistToken(token, expirationDate);
            } catch (Exception e) {

                throw new InvalidOperationException("Invalid token for logout");
            }
        } else {
            throw new InvalidOperationException("Token is required for logout");
        }
    }
}