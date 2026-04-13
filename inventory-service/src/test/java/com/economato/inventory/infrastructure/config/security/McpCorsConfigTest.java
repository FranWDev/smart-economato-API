package com.economato.inventory.infrastructure.config.security;

import com.economato.inventory.infrastructure.config.web.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.LocaleResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class McpCorsConfigTest {

    private static final String NEST_ORIGIN = "http://localhost:3000";
    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        JwtFilter jwtFilter = mock(JwtFilter.class);
        McpServiceGuardFilter mcpServiceGuardFilter = mock(McpServiceGuardFilter.class);
        I18nService i18nService = mock(I18nService.class);
        LocaleResolver localeResolver = mock(LocaleResolver.class);

        securityConfig = new SecurityConfig(jwtFilter, mcpServiceGuardFilter, i18nService, localeResolver, NEST_ORIGIN);
    }

    @Test
    void nestOrigin_allowedOnMcpPath() {
        CorsConfiguration config = getConfig("/api/mcp/context");

        assertNotNull(config);
        assertEquals(NEST_ORIGIN, config.checkOrigin(NEST_ORIGIN));
    }

    @Test
    void frontendOrigin_rejectedOnMcpPath() {
        CorsConfiguration config = getConfig("/api/mcp/context");

        assertNotNull(config);
        assertNull(config.checkOrigin("http://localhost:4200"));
    }

    @Test
    void frontendOrigin_allowedOnOtherPaths() {
        CorsConfiguration config = getConfig("/api/products");

        assertNotNull(config);
        assertEquals("http://localhost:4200", config.checkOrigin("http://localhost:4200"));
    }

    @Test
    void serviceKeyHeader_allowedOnMcpPath() {
        CorsConfiguration config = getConfig("/api/mcp/context");

        assertNotNull(config);
        assertTrue(config.getAllowedHeaders().contains("X-Service-Key"));
        assertTrue(config.getAllowedHeaders().contains("X-User-Language"));
        assertFalse(Boolean.TRUE.equals(config.getAllowCredentials()));
    }

    private CorsConfiguration getConfig(String path) {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return source.getCorsConfiguration(request);
    }
}