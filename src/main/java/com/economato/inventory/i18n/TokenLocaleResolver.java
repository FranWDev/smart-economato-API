package com.economato.inventory.i18n;

import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Locale;

@RequiredArgsConstructor
public class TokenLocaleResolver extends AcceptHeaderLocaleResolver {

    private final Cache<String, Locale> tokenLocaleCache;

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        String token = parseJwt(request);

        if (token != null) {
            Locale cachedLocale = tokenLocaleCache.getIfPresent(token);
            if (cachedLocale != null) {
                return cachedLocale;
            }

            Locale resolvedLocale = super.resolveLocale(request);
            tokenLocaleCache.put(token, resolvedLocale);
            return resolvedLocale;
        }

        return super.resolveLocale(request);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("auth_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
