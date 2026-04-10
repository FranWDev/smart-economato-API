package com.economato.inventory.infrastructure.config.web;

import com.economato.inventory.infrastructure.WebSocketConnectedEvent;
import com.economato.inventory.infrastructure.WebSocketDisconnectedEvent;
import com.economato.inventory.infrastructure.config.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;
    private final I18nService i18nService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-alerts")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        registry.addEndpoint("/ws-notifications")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }

                if (accessor.getUser() instanceof Authentication authentication) {
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }

                StompCommand command = accessor.getCommand();
                if (command == null) {
                    return message;
                }
                switch (command) {
                    case CONNECT -> {
                        String authToken = null;
                        List<String> authorization = accessor.getNativeHeader("Authorization");

                        if (authorization != null && !authorization.isEmpty()) {
                            String bearerToken = authorization.get(0);
                            if (bearerToken.startsWith("Bearer ")) {
                                authToken = bearerToken.substring(7);
                            }
                        }

                        if (authToken == null) {
                            throw new MessageDeliveryException(i18nService.getMessage(MessageKey.ERROR_AUTH_JWT_MISSING));
                        }

                        if (jwtUtils.validateJwtToken(authToken)) {
                            String username = jwtUtils.getUserNameFromJwtToken(authToken);
                            String role = jwtUtils.getRoleFromJwtToken(authToken);

                            if (username != null && role != null) {
                                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                                accessor.setUser(authentication);
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                                log.debug("WebSocket authenticated user: {} with role: {}", username, role);

                                eventPublisher.publishEvent(new WebSocketConnectedEvent(username, accessor.getSessionId()));
                            } else {
                                throw new MessageDeliveryException(
                                        i18nService.getMessage(MessageKey.ERROR_AUTH_UNAUTHORIZED));
                            }
                        } else {
                            log.warn("WebSocket connection attempt with invalid JWT token");
                            throw new MessageDeliveryException(i18nService.getMessage(MessageKey.ERROR_AUTH_JWT_INVALID));
                        }
                    }
                    case SUBSCRIBE -> {
                        String destination = accessor.getDestination();
                        if (destination != null && destination.startsWith("/topic/roles/")) {
                            String requiredRole = destination.substring("/topic/roles/".length()).split("/")[0];
                            UsernamePasswordAuthenticationToken user = (UsernamePasswordAuthenticationToken) accessor.getUser();

                            if (user == null) {
                                throw new MessageDeliveryException("Unauthorized: User not authenticated");
                            }

                            boolean hasRole = user.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_" + requiredRole));

                            if (!hasRole) {
                                log.warn("User {} attempted to subscribe to unauthorized role topic: {}", user.getName(), requiredRole);
                                throw new MessageDeliveryException("Unauthorized: User does not have the required role");
                            }
                        }
                    }
                    case DISCONNECT -> {
                        if (accessor.getUser() != null) {
                            eventPublisher.publishEvent(new WebSocketDisconnectedEvent(accessor.getUser().getName(), accessor.getSessionId()));
                        }
                        SecurityContextHolder.clearContext();
                    }
                    default -> {
                        // no-op
                    }
                }
                return message;
            }
        });
    }
}
