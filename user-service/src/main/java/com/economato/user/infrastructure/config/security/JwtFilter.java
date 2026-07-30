package com.economato.user.infrastructure.config.security;

import com.economato.user.application.port.out.TokenBlacklistPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final TokenBlacklistPort tokenBlacklistPort;
    private static final WebAuthenticationDetailsSource DETAILS_SOURCE = new WebAuthenticationDetailsSource();

    public JwtFilter(JwtUtils jwtUtils, TokenBlacklistPort tokenBlacklistPort) {
        this.jwtUtils = jwtUtils;
        this.tokenBlacklistPort = tokenBlacklistPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String jwt = jwtUtils.resolveToken(request);

        if (jwt != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                boolean isBlacklisted = tokenBlacklistPort.isBlacklisted(jwt);

                if (!isBlacklisted) {
                    String username = jwtUtils.validateAndExtractUsername(jwt);

                    if (username != null) {
                        String role = jwtUtils.getRoleFromJwtToken(jwt);
                        String authorityName = role != null ? "ROLE_" + role : "ROLE_USER";
                        List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(authorityName));

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                authorities
                        );
                        authentication.setDetails(DETAILS_SOURCE.buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (Exception e) {
                log.debug("Error during JWT validation or blacklist check in user-service: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
