package com.economato.inventory.infrastructure.config.security;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
    void doFilter_withValidServiceKey_continuesChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mcp/context");
        request.addHeader("X-Service-Key", "test-service-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_withInvalidServiceKey_returns403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mcp/context");
        request.addHeader("X-Service-Key", "invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(filterChain);
    }

    @Test
    void doFilter_withMissingServiceKey_returns403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mcp/context");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldNotFilter_nonMcpPath_returnsTrue() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");

        boolean result = filter.shouldNotFilter(request);

        assertTrue(result);
    }

    @Test
    void shouldNotFilter_mcpPath_returnsFalse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mcp/context");

        boolean result = filter.shouldNotFilter(request);

        assertFalse(result);
    }

    @Test
    void shouldNotFilter_mcpChatPath_returnsFalse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mcp/chat/chats/1/messages/stream");

        boolean result = filter.shouldNotFilter(request);

        assertFalse(result);
    }
}
