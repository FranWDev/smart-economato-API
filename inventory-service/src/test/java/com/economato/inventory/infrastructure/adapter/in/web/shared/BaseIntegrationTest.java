package com.economato.inventory.infrastructure.adapter.in.web.shared;

import com.economato.inventory.application.usecase.user.CustomUserDetailsService;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.product.ProductRepository;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.config.shared.security.JwtUtils;
import com.economato.inventory.infrastructure.shared.DatabaseCleaner;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @BeforeAll
    static void configureSecurityContextPropagation() {
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    protected CustomUserDetailsService customUserDetailsService;

    @Autowired
    protected CacheManager cacheManager;

    @Autowired
    protected JwtUtils jwtUtils;

    @Autowired
    protected UserRepository userRepository;

    @MockitoSpyBean
    protected ProductRepository productRepository;

    @BeforeEach
    void clearUserDetailsCache() {
        customUserDetailsService.clearCache();
        for (String cacheName : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
        Mockito.doAnswer(inv -> productRepository.findById(inv.getArgument(0)))
                .when(productRepository).findByIdForUpdate(Mockito.any());
    }

    protected String asJsonString(Object obj) {
        try {
            String json = objectMapper.writeValueAsString(obj);
            if (json == null || json.isBlank()) {
                throw new RuntimeException("El JSON generado está vacío");
            }
            return json;
        } catch (Exception e) {
            throw new RuntimeException("Error al serializar objeto a JSON", e);
        }
    }

    protected void clearDatabase() {
        databaseCleaner.clear();
    }

    protected String login(String username, String password) throws Exception {
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        return jwtUtils.generateJwtToken(auth).getToken();
    }

    protected String loginAsAdmin() throws Exception {
        return login("Admin", "admin123");
    }
}
