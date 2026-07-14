package com.economato.inventory.application.usecase.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.user.UserRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private I18nService i18nService;

    private CustomUserDetailsService customUserDetailsService;
    private Cache<String, UserDetails> userDetailsLocalCache;

    private User testUser;

    @BeforeEach
    void setUp() {
        userDetailsLocalCache = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(500)
            .build();
        customUserDetailsService = new CustomUserDetailsService(i18nService, userRepository, userDetailsLocalCache);

        testUser = new User();
        testUser.setId(1);
        testUser.setName("testUser");
        testUser.setUser("testUser");
        testUser.setPassword("encodedPassword");
        testUser.setRole(Role.USER);
        testUser.setHidden(false);
            lenient().when(i18nService.getMessage(any(MessageKey.class)))
                .thenAnswer(invocation -> ((MessageKey) invocation.getArgument(0)).name());
            lenient().when(i18nService.getMessage(any(MessageKey.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    Object arg = invocation.getArgument(1);
                    String argsStr = arg instanceof Object[] ? Arrays.toString((Object[]) arg) : String.valueOf(arg);
                    return ((MessageKey) invocation.getArgument(0)).name() + " " + (argsStr != null ? argsStr : "[]");
                });
    }

    @Test
    void loadUserByUsername_WhenUserExists_ShouldReturnUserDetails() {
        when(userRepository.findByName("testUser")).thenReturn(Optional.of(testUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("testUser");

        assertNotNull(userDetails);
        assertEquals("testUser", userDetails.getUsername());
        assertEquals("encodedPassword", userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        verify(userRepository).findByName("testUser");
    }

    @Test
    void loadUserByUsername_WhenUserDoesNotExist_ShouldThrowException() {
        when(userRepository.findByName("nonExistent")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("nonExistent"));
        verify(userRepository).findByName("nonExistent");
    }

    @Test
    void loadUserByUsername_WhenUserIsHidden_ShouldThrowException() {
        testUser.setHidden(true);
        when(userRepository.findByName("testUser")).thenReturn(Optional.of(testUser));

        assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("testUser"));
        verify(userRepository).findByName("testUser");
    }

    @Test
    void loadUserByUsername_WhenUserIsHiddenWithSpecificMessage_ShouldThrowExceptionWithHiddenMessage() {
        testUser.setHidden(true);
        when(userRepository.findByName("testUser")).thenReturn(Optional.of(testUser));

        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("testUser"));
        assertTrue(exception.getMessage().contains("ERROR_AUTH_USER_HIDDEN"));
    }

    @Test
    void loadUserByUsername_WithAdminRole_ShouldReturnUserDetailsWithAdminAuthority() {
        testUser.setRole(Role.ADMIN);
        when(userRepository.findByName("adminUser")).thenReturn(Optional.of(testUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("adminUser");

        assertNotNull(userDetails);
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        verify(userRepository).findByName("adminUser");
    }

    @Test
    void loadUserByUsername_WithChefRole_ShouldReturnUserDetailsWithChefAuthority() {
        testUser.setRole(Role.CHEF);
        when(userRepository.findByName("chefUser")).thenReturn(Optional.of(testUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("chefUser");

        assertNotNull(userDetails);
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CHEF")));
        verify(userRepository).findByName("chefUser");
    }

    @Test
    void loadUserByUsername_WhenCalledMultipleTimes_ShouldUseCacheOnSecondCall() {
        when(userRepository.findByName("testUser")).thenReturn(Optional.of(testUser));

        // Primera llamada
        UserDetails userDetails1 = customUserDetailsService.loadUserByUsername("testUser");
        // Segunda llamada
        UserDetails userDetails2 = customUserDetailsService.loadUserByUsername("testUser");

        assertNotNull(userDetails1);
        assertNotNull(userDetails2);
        // Debería ser llamado solo una vez por el cache
        verify(userRepository, times(1)).findByName("testUser");
    }

    @Test
    void loadUserByUsername_WhenCalledAfterEvict_ShouldFetchFromRepository() {
        when(userRepository.findByName("testUser")).thenReturn(Optional.of(testUser));

        // Primera llamada
        customUserDetailsService.loadUserByUsername("testUser");
        // Evict del cache
        customUserDetailsService.evictUser("testUser");
        // Segunda llamada
        customUserDetailsService.loadUserByUsername("testUser");

        // Debería ser llamado dos veces (una antes del evict, una después)
        verify(userRepository, times(2)).findByName("testUser");
    }

    @Test
    void loadUserByUsername_WhenCalledAfterClearCache_ShouldFetchFromRepository() {
        when(userRepository.findByName("testUser")).thenReturn(Optional.of(testUser));

        // Primera llamada
        customUserDetailsService.loadUserByUsername("testUser");
        // Clear del cache
        customUserDetailsService.clearCache();
        // Segunda llamada
        customUserDetailsService.loadUserByUsername("testUser");

        // Debería ser llamado dos veces
        verify(userRepository, times(2)).findByName("testUser");
    }

    @Test
    void loadUserByUsername_WithHiddenUserAndAdmin_ShouldStillThrowException() {
        testUser.setHidden(true);
        testUser.setRole(Role.ADMIN);
        when(userRepository.findByName("adminUser")).thenReturn(Optional.of(testUser));

        assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("adminUser"));
        assertTrue(customUserDetailsService.toString() != null); // Trivial assertion for test structure
        verify(userRepository).findByName("adminUser");
    }
}
