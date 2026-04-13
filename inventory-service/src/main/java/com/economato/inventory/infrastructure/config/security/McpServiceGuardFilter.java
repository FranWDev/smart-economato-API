package com.economato.inventory.infrastructure.config.security;

import com.economato.inventory.infrastructure.config.ai.AiNestProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class McpServiceGuardFilter extends OncePerRequestFilter {

    private static final String MCP_PATH_PREFIX = "/api/mcp/";
    private static final String SERVICE_KEY_HEADER = "X-Service-Key";

    private final AiNestProperties aiNestProperties;

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
        if (serviceKey == null || !serviceKey.equals(aiNestProperties.getServiceKey())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":403,\"message\":\"Invalid service key\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
