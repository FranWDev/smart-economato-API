package com.economato.inventory.infrastructure.config.shared.security;

import com.economato.inventory.application.usecase.user.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;

    private static final WebAuthenticationDetailsSource DETAILS_SOURCE = new WebAuthenticationDetailsSource();

    private static final Set<String> PUBLIC_URLS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/login",
            "/",
            "/scalar",
            "/scalar-ui.html");

    private static final Set<String> STATIC_PREFIXES = Set.of(
            "/styles/",
            "/scripts/",
            "/swagger-ui/",
            "/v3/api-docs",
            "/webjars/",
            "/swagger-resources/",
            "/configuration/",
            "/scalar/",
            "/robots.txt",
            "/sitemap.xml",
            "/manifest.json");

    public JwtFilter(JwtUtils jwtUtils,
                     TokenBlacklistService tokenBlacklistService) {
        this.jwtUtils = jwtUtils;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String jwt = jwtUtils.resolveToken(request);

        if (jwt != null) {
            try {
                boolean isBlacklisted = tokenBlacklistService.isBlacklisted(jwt);
                
                if (!isBlacklisted) {
                    String username = jwtUtils.validateAndExtractUsername(jwt);

                    if (username != null) {
                        request.setAttribute("jwt_username", username);

                        String role = jwtUtils.getRoleFromJwtToken(jwt);
                        String authorityName = (role != null) ? (role.startsWith("ROLE_") ? role : "ROLE_" + role) : "ROLE_USER";
                        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(authorityName));

                        UserDetails userDetails = new User(username, "", authorities);
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                authorities);
                        authentication.setDetails(DETAILS_SOURCE.buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (Exception e) {
                log.debug("Error during JWT validation or blacklist check in inventory-service: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();

        if (PUBLIC_URLS.contains(path)) {
            return true;
        }

        for (String prefix : STATIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }

        if (path.startsWith("/api/auth/") &&
                !path.equals("/api/auth/validate") &&
                !path.equals("/api/auth/logout") &&
                !path.equals("/api/auth/role")) {
            return true;
        }

        return false;
    }
}
