package com.red_escuela.backend.identity.infrastructure;

import com.red_escuela.backend.identity.exception.RefreshCsrfRejectedException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshCookieSession {

    public static final String COOKIE_NAME = "refresh_token";
    public static final String CSRF_HEADER = "X-Requested-With";
    public static final String CSRF_HEADER_VALUE = "fetch";

    private final String path;
    private final boolean secure;
    private final String sameSite;
    private final Duration maxAge;
    private final List<String> allowedOrigins;

    public RefreshCookieSession(
            @Value("${app.auth.refresh-cookie-path:/api/auth}") String path,
            @Value("${app.auth.refresh-cookie-secure:true}") boolean secure,
            @Value("${app.auth.refresh-cookie-same-site:Strict}") String sameSite,
            @Value("${jwt.refresh-token-expiration-days}") long refreshTokenExpirationDays,
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.path = path;
        this.secure = secure;
        this.sameSite = sameSite;
        this.maxAge = Duration.ofDays(refreshTokenExpirationDays);
        this.allowedOrigins = allowedOrigins;
    }

    public String issuedCookie(String refreshToken) {
        return build(refreshToken, maxAge).toString();
    }

    public String expiredCookie() {
        return build("", Duration.ZERO).toString();
    }

    public Optional<String> resolveToken(HttpServletRequest request, String bodyToken) {
        Optional<String> cookieToken = cookieToken(request);
        if (cookieToken.isPresent()) {
            requireSameSiteIntent(request);
            return cookieToken;
        }
        return Optional.ofNullable(bodyToken).filter(token -> !token.isBlank());
    }

    private Optional<String> cookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private void requireSameSiteIntent(HttpServletRequest request) {
        if (!CSRF_HEADER_VALUE.equals(request.getHeader(CSRF_HEADER))) {
            throw new RefreshCsrfRejectedException("falta la cabecera " + CSRF_HEADER);
        }
        String origin = request.getHeader("Origin");
        if (origin != null && !allowedOrigins.contains(origin)) {
            throw new RefreshCsrfRejectedException("origen no permitido");
        }
    }

    private ResponseCookie build(String value, Duration age) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(age)
                .build();
    }
}
