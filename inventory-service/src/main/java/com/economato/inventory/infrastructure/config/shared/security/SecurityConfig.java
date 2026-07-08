package com.economato.inventory.infrastructure.config.shared.security;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.config.mcp.security.McpServiceGuardFilter;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.LocaleResolver;

import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

        private final JwtFilter jwtFilter;
        private final McpServiceGuardFilter mcpServiceGuardFilter;
        private final I18nService i18nService;
        private final LocaleResolver localeResolver;
        private final String aiNestAllowedOrigin;

        public SecurityConfig(JwtFilter jwtFilter,
                        McpServiceGuardFilter mcpServiceGuardFilter,
                        I18nService i18nService,
                        LocaleResolver localeResolver,
                        @Value("${ai.nest.allowed-origin:http://localhost}") String aiNestAllowedOrigin) {
                this.jwtFilter = jwtFilter;
                this.mcpServiceGuardFilter = mcpServiceGuardFilter;
                this.i18nService = i18nService;
                this.localeResolver = localeResolver;
                this.aiNestAllowedOrigin = aiNestAllowedOrigin;
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                return http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .csrf(csrf -> csrf.disable())
                                .authorizeHttpRequests(auth -> auth
                                                // Los dispatches internos son continuaciones de requests ya autenticadas.
                                                // El JwtFilter (OncePerRequestFilter) no corre en ASYNC, por lo que
                                                // hay que permitirlos explícitamente para que StreamingResponseBody
                                                // funcione. También permitimos ERROR para evitar que las excepciones
                                                // manejadas por el BasicErrorController retornen 401.
                                                // INCLUDE/FORWARD también aparecen en el flujo de ErrorPage de Tomcat.
                                                .dispatcherTypeMatchers(
                                                                DispatcherType.ASYNC,
                                                                DispatcherType.ERROR,
                                                                DispatcherType.INCLUDE,
                                                                DispatcherType.FORWARD)
                                                .permitAll()

                                                // Autenticación
                                                .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()

                                                // Vistas públicas
                                                .requestMatchers("/login", "/")
                                                .permitAll()

                                                // Recursos estáticos
                                                .requestMatchers("/styles/**", "/scripts/**").permitAll()
                                                .requestMatchers("/robots.txt", "/sitemap.xml", "/manifest.json")
                                                .permitAll()

                                                // Documentación Swagger
                                                .requestMatchers("/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
                                                                "/swagger-ui.html", "/webjars/**",
                                                                "/swagger-resources/**", "/configuration/**",
                                                                "/scalar-ui.html", "/scalar", "/scalar/**")
                                                .permitAll()

                                                // WebSocket handshake
                                                .requestMatchers("/ws-alerts/**", "/ws-notifications", "/ws-notifications/**")
                                                .permitAll()

                                                // Health endpoint: público para el healthcheck de Docker
                                                // (viene de 127.0.0.1, no de 172.19.x.x)
                                                .requestMatchers("/actuator/health", "/actuator/health/**")
                                                .permitAll()

                                                // Prometheus: solo desde la subred interna Docker
                                                .requestMatchers("/actuator/prometheus")
                                                .access((authentication, request) -> {
                                                        String clientIp = request.getRequest().getRemoteAddr();
                                                        return new AuthorizationDecision(
                                                                        clientIp != null && (
                                                                                clientIp.startsWith("172.") ||
                                                                                clientIp.startsWith("10.") ||
                                                                                clientIp.equals("127.0.0.1")));
                                                })

                                                // El control de acceso específico se maneja con @PreAuthorize en los
                                                // controladores
                                                // Todas las demás rutas requieren autenticación
                                                .anyRequest().authenticated())
                                .headers(headers -> headers
                                                // HSTS - Force HTTPS
                                                .httpStrictTransportSecurity(hsts -> hsts
                                                                .includeSubDomains(true)
                                                                .maxAgeInSeconds(31536000))
                                                // Content Security Policy (relajado para Swagger UI)
                                                .contentSecurityPolicy(csp -> csp
                                                                .policyDirectives("default-src 'self'; " +
                                                                                "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net; "
                                                                                +
                                                                                "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                                                                                "img-src 'self' data: https:; " +
                                                                                "font-src 'self' data: https://fonts.scalar.com; " +
                                                                                "connect-src 'self' https://cdn.jsdelivr.net; " +
                                                                                "frame-ancestors 'none'; " +
                                                                                "base-uri 'self'; " +
                                                                                "form-action 'self'; " +
                                                                                "upgrade-insecure-requests"))
                                                .frameOptions(frame -> frame.deny())
                                                .contentTypeOptions(contentType -> {
                                                })
                                                .xssProtection(xss -> {
                                                })
                                                .referrerPolicy(referrer -> referrer.policy(
                                                                ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .exceptionHandling(exceptions -> exceptions
                                                .authenticationEntryPoint((request, response, authException) -> {
                                                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                                        response.setContentType("application/json");
                                                        Locale locale = localeResolver.resolveLocale(request);
                                                        String message = i18nService.getMessage(
                                                                        MessageKey.ERROR_AUTH_UNAUTHORIZED, locale);
                                                        response.getWriter().write(
                                                                        String.format("{\"status\":401,\"message\":\"%s\"}",
                                                                                        message));
                                                }))
                                .addFilterBefore(mcpServiceGuardFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                                .build();
        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
                return authConfig.getAuthenticationManager();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration mcpConfiguration = new CorsConfiguration();
                mcpConfiguration.setAllowedOrigins(List.of(aiNestAllowedOrigin));
                mcpConfiguration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                mcpConfiguration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Service-Key",
                                "X-User-Language"));
                mcpConfiguration.setAllowCredentials(false);
                mcpConfiguration.setMaxAge(86400L);

                CorsConfiguration configuration = new CorsConfiguration();
                // Permitir dev local + LAN y dominio de producción.
                // Nota: con allowCredentials=true no se puede usar "*" en allowedOrigins.
                configuration.setAllowedOriginPatterns(Arrays.asList(
                                "http://localhost",
                                "http://localhost:*",
                                "https://localhost",
                                "https://localhost:*",
                                "http://127.0.0.1",
                                "http://127.0.0.1:*",
                                "https://127.0.0.1",
                                "https://127.0.0.1:*",
                                "http://192.168.8.182",
                                "http://192.168.8.182:*",
                                "https://192.168.8.182",
                                "https://192.168.8.182:*",
                                "http://192.168.*.*",
                                "http://192.168.*.*:*",
                                "https://192.168.*.*",
                                "https://192.168.*.*:*",
                                "http://smart-economato",
                                "https://smart-economato",
                                "https://economato.servehttp.com"));
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
                configuration.setAllowedHeaders(
                                Arrays.asList("Authorization", "Content-Type", "Accept", "Origin", "X-User-Language"));
                configuration.setExposedHeaders(List.of("Authorization"));
                configuration.setAllowCredentials(true);
                configuration.setMaxAge(86400L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/api/mcp/**", mcpConfiguration);
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }
}
