package com.economatom.inventory.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"status\":401,\"message\":\"No autorizado\"}");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // Rutas públicas - Autenticación (excepto validate que requiere token)
                        .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                        // Rutas públicas - Vistas (login, etc)
                        .requestMatchers("/login").permitAll()
                        // Rutas públicas - Recursos estáticos (todos los scripts y estilos)
                        .requestMatchers("/styles/**").permitAll()
                        .requestMatchers("/scripts/**").permitAll()
                        // Swagger UI (documentación)
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        
                        // ===== CONTROL DE ACCESO POR ROL =====
                        // ADMIN - acceso total a todo
                        .requestMatchers("/api/products/**").hasAnyRole("ADMIN", "CHEF")
                        .requestMatchers("/api/inventory-movements/**").hasAnyRole("ADMIN", "CHEF")
                        .requestMatchers("/api/recipes/**").hasAnyRole("ADMIN", "CHEF")
                        .requestMatchers("/api/orders/**").hasAnyRole("ADMIN", "CHEF", "USER")
                        
                        // DELETE solo para ADMIN
                        .requestMatchers(HttpMethod.DELETE).hasRole("ADMIN")
                        
                        // El resto requiere autenticación
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // TODO: Configurar dominios específicos en producción
        configuration.setAllowedOriginPatterns(List.of("*")); // Permite todos los orígenes temporalmente
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
