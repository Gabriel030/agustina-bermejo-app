package com.red_escuela.backend.identity.infrastructure;

import com.red_escuela.backend.identity.exception.RefreshCsrfRejectedException;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshCookieSessionTest {

    private final RefreshCookieSession session = new RefreshCookieSession(
            "/api/auth", true, "Strict", 7, List.of("https://escuela.example.com"));

    private MockHttpServletRequest requestWithCookie(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(RefreshCookieSession.COOKIE_NAME, value));
        return request;
    }

    @Test
    void issuedCookieCarriesHttpOnlySecureSameSiteStrictPathAndSevenDayMaxAge() {
        String cookie = session.issuedCookie("abc");

        assertThat(cookie).startsWith("refresh_token=abc;")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/api/auth")
                .contains("Max-Age=604800");
    }

    @Test
    void expiredCookieHasEmptyValueAndZeroMaxAgeOnTheSamePath() {
        String cookie = session.expiredCookie();

        assertThat(cookie).startsWith("refresh_token=;")
                .contains("Max-Age=0")
                .contains("Path=/api/auth")
                .contains("HttpOnly");
    }

    @Test
    void cookieWithCustomHeaderAndAllowedOriginIsAccepted() {
        MockHttpServletRequest request = requestWithCookie("from-cookie");
        request.addHeader("X-Requested-With", "fetch");
        request.addHeader("Origin", "https://escuela.example.com");

        assertThat(session.resolveToken(request, "from-body")).contains("from-cookie");
    }

    @Test
    void cookieWithoutCustomHeaderIsRejected() {
        MockHttpServletRequest request = requestWithCookie("from-cookie");

        assertThatThrownBy(() -> session.resolveToken(request, null)).isInstanceOf(RefreshCsrfRejectedException.class);
    }

    @Test
    void cookieFromForeignOriginIsRejectedEvenWithTheCustomHeader() {
        MockHttpServletRequest request = requestWithCookie("from-cookie");
        request.addHeader("X-Requested-With", "fetch");
        request.addHeader("Origin", "https://evil.example.org");

        assertThatThrownBy(() -> session.resolveToken(request, null)).isInstanceOf(RefreshCsrfRejectedException.class);
    }

    @Test
    void bodyTokenIsUsedWhenThereIsNoCookieAndNeedsNoCsrfHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(session.resolveToken(request, "from-body")).contains("from-body");
    }

    @Test
    void blankBodyTokenAndNoCookieResolvesToNothing() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(session.resolveToken(request, " ")).isEmpty();
        assertThat(session.resolveToken(request, null)).isEmpty();
    }

    @Test
    void blankCookieFallsBackToTheBody() {
        MockHttpServletRequest request = requestWithCookie("");

        assertThat(session.resolveToken(request, "from-body")).contains("from-body");
    }
}
