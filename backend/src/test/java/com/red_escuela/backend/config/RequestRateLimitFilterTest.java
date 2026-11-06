package com.red_escuela.backend.config;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RequestRateLimitFilterTest {

    private final RequestRateLimitFilter filter = new RequestRateLimitFilter(
            new JsonErrorResponseWriter(new ObjectMapper()), Clock.systemUTC(), 1000, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 1, 1);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse call(String method, String path, String remoteAddr) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddr);
        request.addHeader("X-Forwarded-For", "9.9." + System.nanoTime() % 200 + ".1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        return response;
    }

    private void assertLimited(String method, String path) throws Exception {
        assertThat(call(method, path, "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(call(method, path, "10.0.0.1").getStatus()).isEqualTo(200);
        MockHttpServletResponse limited = call(method, path, "10.0.0.1");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(limited.getHeader("Retry-After"))).isPositive();
        assertThat(limited.getContentAsString()).contains("\"retryable\":true").contains("REQUEST_RATE_LIMIT_EXCEEDED");
        assertThat(call(method, path, "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void refreshIsLimitedPerIpAndAnswersRetryAfter() throws Exception {
        assertLimited("POST", "/auth/refresh");
    }

    @Test
    void resetPasswordIsLimitedPerIp() throws Exception {
        assertLimited("POST", "/auth/reset-password");
    }

    @Test
    void publicProspectCreationIsLimitedPerIp() throws Exception {
        assertLimited("POST", "/prospects");
    }

    @Test
    void staffProspectsLimiterBlocksAfterCapacityPerUser() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L, null, java.util.List.of()));

        assertThat(call("POST", "/prospects/by-staff", "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/prospects/by-staff", "10.0.0.1").getStatus()).isEqualTo(200);
        MockHttpServletResponse limited = call("POST", "/prospects/by-staff", "10.0.0.1");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotNull();
        assertThat(call("PUT", "/me", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void staffProspectsLimiterDoesNotShareBucketBetweenUsers() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L, null, java.util.List.of()));
        call("POST", "/prospects/by-staff", "10.0.0.1");
        call("POST", "/prospects/by-staff", "10.0.0.1");
        assertThat(call("POST", "/prospects/by-staff", "10.0.0.1").getStatus()).isEqualTo(429);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(8L, null, java.util.List.of()));
        assertThat(call("POST", "/prospects/by-staff", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void theForwardedForHeaderIsIgnoredSoItCannotBeUsedToEvadeTheLimit() throws Exception {
        call("POST", "/prospects", "10.0.0.5");
        call("POST", "/prospects", "10.0.0.5");

        assertThat(call("POST", "/prospects", "10.0.0.5").getStatus()).isEqualTo(429);
    }

    @Test
    void otherAnonymousRoutesAreNotLimitedByThisFilter() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("GET", "/institutions", "10.0.0.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void authenticatedWritesAreLimitedPerUserAndReadsHaveTheirOwnBudget() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L, null, java.util.List.of()));

        assertThat(call("PUT", "/me", "10.0.0.1").getStatus()).isEqualTo(200);
        MockHttpServletResponse limited = call("DELETE", "/something/1", "10.0.0.1");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotNull();
        assertThat(call("GET", "/me", "10.0.0.1").getStatus()).isEqualTo(200);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(8L, null, java.util.List.of()));
        assertThat(call("PUT", "/me", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void theChainIsNotInvokedWhenTheRequestIsRejected() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/refresh");
            request.setRemoteAddr("10.0.0.9");
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }

        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
