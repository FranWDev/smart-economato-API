package com.economato.inventory.security;

import com.economato.inventory.config.JwtProperties;
import com.economato.inventory.dto.response.LoginResponseDTO;
import com.economato.inventory.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtils {

    private final JwtProperties jwtProperties;
    private final SecretKey key;
    private final JwtParser jwtParser;
    private static final MacAlgorithm ALG = Jwts.SIG.HS256;

    public JwtUtils(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));
        this.jwtParser = Jwts.parser().verifyWith(key).build();
    }

    public LoginResponseDTO generateJwtToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();

        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("ROLE_USER");

        String cleanRole = role.replace("ROLE_", "");
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getExpiration());

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