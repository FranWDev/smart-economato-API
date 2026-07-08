package com.economato.inventory.infrastructure.config.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.economato.inventory.application.usecase.user.TokenBlacklistService;

import java.io.IOException;
import java.util.Set;

@Slf4j
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;
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

    public JwtFilter(JwtUtils jwtUtils, UserDetailsService userDetailsService,
            TokenBlacklistService tokenBlacklistService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
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

                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities());
                        authentication.setDetails(DETAILS_SOURCE.buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (Exception e) {
                // Logea los errores de validación del JWT o de la consulta a la blacklist, pero no bloquea la solicitud
                log.debug("Error during JWT validation or blacklist check: {}", e.getMessage());
                // El token no es válido o está en la blacklist, pero no se bloquea la solicitud aquí 
                // porque el filtro de seguridad de Spring Security se encargará de rechazarla si el endpoint requiere autenticación 
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

