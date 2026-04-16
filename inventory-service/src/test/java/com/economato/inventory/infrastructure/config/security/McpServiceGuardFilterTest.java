package com.economato.inventory.infrastructure.config.security;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

@ExtendWith(MockitoExtension.class)
class McpServiceGuardFilterTest {

    @Mock
    private FilterChain filterChain;

    private McpServiceGuardFilter filter;

    @BeforeEach
    void setUp() {
        filter = new McpServiceGuardFilter("test-service-key");
    }

    @Test
    void correctServiceKey_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mcp/context");
        request.addHeader("X-Service-Key", "test-service-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void incorrectServiceKey_returns403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mcp/context");
        request.addHeader("X-Service-Key", "invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(filterChain);
    }

    @Test
    void missingServiceKey_returns403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mcp/context");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(filterChain);
    }

    @Test
    void nonMcpPath_doesNotFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");

        boolean result = filter.shouldNotFilter(request);

        assertTrue(result);
    }

    @Test
    void mcpPath_alwaysFilters() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mcp/context");

        boolean result = filter.shouldNotFilter(request);

        assertFalse(result);
    }

    @Test
    void chatPath_doesNotFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat/chats/1/messages/stream");

        boolean result = filter.shouldNotFilter(request);

        assertTrue(result);
    }

    @Test
    void errorResponse_isJsonFormatted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mcp/context");
        request.addHeader("X-Service-Key", "bad-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().startsWith("{"));
        assertTrue(response.getContentAsString().contains("message"));
        verifyNoInteractions(filterChain);
    }
}
