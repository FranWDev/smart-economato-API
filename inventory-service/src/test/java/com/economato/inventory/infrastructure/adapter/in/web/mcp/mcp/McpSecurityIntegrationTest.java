package com.economato.inventory.infrastructure.adapter.in.web.mcp.mcp;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.economato.inventory.application.usecase.mcp.mcp.McpToolReadService;
import com.economato.inventory.application.usecase.user.TokenBlacklistService;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.in.web.shared.BaseIntegrationTest;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.config.shared.security.JwtProperties;
import com.economato.inventory.infrastructure.shared.TestDataUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class McpSecurityIntegrationTest extends BaseIntegrationTest {

    private static final String SERVICE_KEY = "test-service-key-for-integration-tests";
    private static final String NEST_ORIGIN = "http://localhost:9999";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private McpToolReadService mcpToolReadService;

    @BeforeEach
    void setUp() {
        clearDatabase();
        userRepository.saveAndFlush(TestDataUtil.createAdminUser());
        when(mcpToolReadService.getActiveAlerts()).thenReturn(List.of());
        tokenBlacklistService.clearBlacklist();
    }

    @Test
    void mcpEndpoint_withServiceKeyAndJwt_returns200() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/mcp/alerts/active")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void mcpEndpoint_withoutServiceKey_returns403() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/mcp/alerts/active")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void mcpEndpoint_withWrongServiceKey_returns403() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/mcp/alerts/active")
                        .header("X-Service-Key", "wrong-key")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void mcpEndpoint_withServiceKeyButNoJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/mcp/alerts/active")
                        .header("X-Service-Key", SERVICE_KEY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mcpEndpoint_withBlacklistedJwt_returns401() throws Exception {
        String token = loginAsAdmin();
        tokenBlacklistService.blacklistToken(token, new Date(System.currentTimeMillis() + 3600000L));

        mockMvc.perform(get("/api/mcp/alerts/active")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mcpEndpoint_withExpiredJwt_returns401() throws Exception {
        String expiredToken = buildExpiredAdminToken();

        mockMvc.perform(get("/api/mcp/alerts/active")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonMcpEndpoint_withoutServiceKey_allowed() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/products")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void mcpEndpoint_corsFromNestOrigin_allowed() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(options("/api/mcp/alerts/active")
                .header("Origin", NEST_ORIGIN)
                        .header("Access-Control-Request-Method", "GET")
                        .header("X-Service-Key", SERVICE_KEY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void mcpEndpoint_corsFromFrontendOrigin_blocked() throws Exception {
        mockMvc.perform(options("/api/mcp/alerts/active")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mcpEndpoint_corsHeaders_includeServiceKeyAndLanguage() throws Exception {
        mockMvc.perform(options("/api/mcp/alerts/active")
                        .header("Origin", NEST_ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "X-Service-Key, X-User-Language"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers",
                        allOf(containsString("X-Service-Key"), containsString("X-User-Language"))));
    }

    private String buildExpiredAdminToken() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        Date now = new Date();
        Date expired = new Date(now.getTime() - TimeUnit.MINUTES.toMillis(1));
        Date issuedAt = new Date(now.getTime() - TimeUnit.MINUTES.toMillis(5));

        return Jwts.builder()
                .subject("Admin")
                .claim("role", "ADMIN")
                .issuedAt(issuedAt)
                .expiration(expired)
                .signWith(Keys.hmacShaKeyFor(keyBytes), Jwts.SIG.HS256)
                .compact();
    }
}