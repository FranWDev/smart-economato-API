package com.economato.inventory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.economato.inventory.i18n.I18nService;
import com.economato.inventory.i18n.MessageKey;
import com.economato.inventory.model.Role;
import com.economato.inventory.model.User;
import com.economato.inventory.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private I18nService i18nService;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1);
        testUser.setName("testUser");
        testUser.setUser("testUser");
        testUser.setPassword("encodedPassword");
        testUser.setRole(Role.USER);
        testUser.setHidden(false);
        lenient().when(i18nService.getMessage(any(MessageKey.class)))
                .thenAnswer(invocation -> ((MessageKey) invocation.getArgument(0)).name());
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
