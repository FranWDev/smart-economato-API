package com.economato.inventory.infrastructure.config.mcp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class McpServiceGuardFilter extends OncePerRequestFilter {

    private static final String MCP_PATH_PREFIX = "/api/mcp/";
    private static final String SERVICE_KEY_HEADER = "X-Service-Key";

    private final String expectedServiceKey;

    public McpServiceGuardFilter(@Value("${ai.nest.service-key:}") String expectedServiceKey) {
        this.expectedServiceKey = expectedServiceKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith(MCP_PATH_PREFIX)) {
            return true;
        }
        return HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String serviceKey = request.getHeader(SERVICE_KEY_HEADER);
        if (expectedServiceKey == null || expectedServiceKey.isBlank() || serviceKey == null || !serviceKey.equals(expectedServiceKey)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":403,\"message\":\"Invalid service key\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
