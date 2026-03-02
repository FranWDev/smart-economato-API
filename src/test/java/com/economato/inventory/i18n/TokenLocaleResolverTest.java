package com.economato.inventory.i18n;

import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenLocaleResolverTest {

    private TokenLocaleResolver tokenLocaleResolver;

    @Mock
    private Cache<String, Locale> tokenLocaleCache;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        tokenLocaleResolver = new TokenLocaleResolver(tokenLocaleCache);
    }

    @Test
    void testResolveLocaleWithTokenCachesResult() {
        String token = "valid.jwt.token";
        Locale expectedLocale = Locale.FRENCH;

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenLocaleCache.getIfPresent(token)).thenReturn(null);
        when(request.getLocale()).thenReturn(expectedLocale);

        Locale actualLocale = tokenLocaleResolver.resolveLocale(request);

        assertEquals(expectedLocale, actualLocale);
        verify(tokenLocaleCache).put(token, expectedLocale);
    }

    @Test
    void testResolveLocaleWithTokenUsesCache() {
        String token = "valid.jwt.token";
        Locale cachedLocale = Locale.GERMAN;

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenLocaleCache.getIfPresent(token)).thenReturn(cachedLocale);

        Locale actualLocale = tokenLocaleResolver.resolveLocale(request);

        assertEquals(cachedLocale, actualLocale);
        verify(tokenLocaleCache, never()).put(anyString(), any(Locale.class));
    }

    @Test
    void testResolveLocaleWithoutTokenDoesNotCache() {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getLocale()).thenReturn(Locale.ITALIAN);

        Locale actualLocale = tokenLocaleResolver.resolveLocale(request);

        assertEquals(Locale.ITALIAN, actualLocale);
        verifyNoInteractions(tokenLocaleCache);
    }
}
