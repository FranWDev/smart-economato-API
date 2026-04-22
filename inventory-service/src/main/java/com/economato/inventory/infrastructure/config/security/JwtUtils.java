package com.economato.inventory.infrastructure.config.security;

import com.economato.inventory.application.usecase.SystemConfigService;
import com.economato.inventory.application.dto.response.LoginResponseDTO;
import com.economato.inventory.domain.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtils {

    private final JwtProperties jwtProperties;
    private final SecretKey key;
    private final JwtParser jwtParser;
    private static final MacAlgorithm ALG = Jwts.SIG.HS256;
    @Autowired(required = false)
    @Lazy
    private SystemConfigService systemConfigService;

    public JwtUtils(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        String secret = jwtProperties.getSecret();
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (io.jsonwebtoken.io.DecodingException e) {
            try {
                keyBytes = Decoders.BASE64URL.decode(secret);
            } catch (io.jsonwebtoken.io.DecodingException ex) {
                keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.jwtParser = Jwts.parser().verifyWith(key).build();
    }

    public String resolveToken(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("auth_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    public LoginResponseDTO generateJwtToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();

        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("ROLE_USER");

        String cleanRole = role.replace("ROLE_", "");
        Date now = new Date();
        long expiration = jwtProperties.getExpiration();
        if (systemConfigService != null) {
            try {
                expiration = systemConfigService.getJwtExpirationMs();
            } catch (Exception ignored) {
                expiration = jwtProperties.getExpiration();
            }
        }
        Date expiryDate = new Date(now.getTime() + expiration);

        String token = Jwts.builder()
                .subject(userPrincipal.getUsername())
                .claim("role", cleanRole)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key, ALG)
                .compact();

        return new LoginResponseDTO(token, Role.valueOf(cleanRole));
    }

    public String validateAndExtractUsername(String token) {
        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public String getUserNameFromJwtToken(String token) {
        return jwtParser.parseSignedClaims(token).getPayload().getSubject();
    }

    public String getRoleFromJwtToken(String token) {
        return jwtParser.parseSignedClaims(token).getPayload().get("role", String.class);
    }

    public Date getExpirationDateFromJwtToken(String token) {
        return jwtParser.parseSignedClaims(token).getPayload().getExpiration();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            jwtParser.parseSignedClaims(authToken);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}