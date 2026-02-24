package com.economato.inventory.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.economato.inventory.service.TokenBlacklistService;

import java.io.IOException;
import java.util.Set;

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
            "/");

    private static final Set<String> STATIC_PREFIXES = Set.of(
            "/styles/",
            "/scripts/",
            "/swagger-ui/",
            "/v3/api-docs",
            "/webjars/",
            "/swagger-resources/",
            "/configuration/",
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

        String jwt = parseJwt(request);

        if (jwt != null && !tokenBlacklistService.isBlacklisted(jwt)) {
            String username = jwtUtils.validateAndExtractUsername(jwt);

            if (username != null) {
                // Cache username in request for audit/other components
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

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();

        // Check exact public URLs
        if (PUBLIC_URLS.contains(path)) {
            return true;
        }

        // Check static prefixes
        for (String prefix : STATIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }

        // Special case for /api/auth/ public sub-paths
        if (path.startsWith("/api/auth/") &&
                !path.equals("/api/auth/validate") &&
                !path.equals("/api/auth/logout") &&
                !path.equals("/api/auth/role")) {
            return true;
        }

        return false;
    }

    private String parseJwt(HttpServletRequest request) {
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
}
