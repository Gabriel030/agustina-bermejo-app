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
            new JsonErrorResponseWriter(new ObjectMapper()), Clock.systemUTC(), 1000, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 1, 1, 1000, 1000, true);

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
    void monitoringTunnelIsLimitedPerIp() throws Exception {
        assertLimited("POST", "/public/monitoring/tunnel");
    }

    @Test
    void resetPasswordIsLimitedPerIp() throws Exception {
        assertLimited("POST", "/auth/reset-password");
    }

    @Test
    void activateAccountIsLimitedPerIp() throws Exception {
        assertLimited("POST", "/auth/activate");
    }

    @Test
    void activationTokenStatusIsLimitedPerIp() throws Exception {
        assertLimited("POST", "/auth/activation-token/status");
    }

    @Test
    void emailChangeTokenEndpointsAreLimitedPerIp() throws Exception {
        assertLimited("POST", "/email-change-preview");
        assertLimited("POST", "/approve-email-change");
        assertLimited("POST", "/cancel-email-change");
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

    private static final String TUNNEL = "/public/monitoring/tunnel";

    private RequestRateLimitFilter tunnelFilter(int perIpCapacity, int globalCapacity, boolean tunnelActive) {
        return new RequestRateLimitFilter(new JsonErrorResponseWriter(new ObjectMapper()), Clock.systemUTC(), 1000,
                perIpCapacity, perIpCapacity, 2, 2, 2, 2, 2, 2, 3, 3, 1, 1, globalCapacity, globalCapacity, tunnelActive);
    }

    private MockHttpServletResponse callWith(RequestRateLimitFilter target, String path, String remoteAddr) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddr);
        MockHttpServletResponse response = new MockHttpServletResponse();
        target.doFilter(request, response, mock(FilterChain.class));
        return response;
    }

    @Test
    void tunnelIsLimitedGloballyAcrossDistinctIpsEvenWhenNoIpExhaustedItsBucket() throws Exception {
        RequestRateLimitFilter target = tunnelFilter(5, 3, true);
        for (int index = 1; index <= 3; index++) {
            assertThat(callWith(target, TUNNEL, "10.1.0." + index).getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse limited = callWith(target, TUNNEL, "10.1.0.4");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(limited.getHeader("Retry-After"))).isPositive();
        assertThat(limited.getContentAsString()).contains("REQUEST_RATE_LIMIT_EXCEEDED");
    }

    @Test
    void anIpLimitedPerIpDoesNotConsumeTheGlobalBucket() throws Exception {
        RequestRateLimitFilter target = tunnelFilter(1, 2, true);
        assertThat(callWith(target, TUNNEL, "10.2.0.1").getStatus()).isEqualTo(200);
        for (int attempt = 0; attempt < 10; attempt++) {
            assertThat(callWith(target, TUNNEL, "10.2.0.1").getStatus()).isEqualTo(429);
        }
        assertThat(callWith(target, TUNNEL, "10.2.0.2").getStatus()).isEqualTo(200);
        assertThat(callWith(target, TUNNEL, "10.2.0.3").getStatus()).isEqualTo(429);
    }

    @Test
    void inactiveTunnelDoesNotConsumeTheGlobalBucket() throws Exception {
        RequestRateLimitFilter target = tunnelFilter(50, 1, false);
        for (int index = 1; index <= 10; index++) {
            assertThat(callWith(target, TUNNEL, "10.3.0." + index).getStatus()).isEqualTo(200);
        }
    }

    @Test
    void otherPublicEndpointsAreNotAffectedByTheGlobalTunnelLimit() throws Exception {
        RequestRateLimitFilter target = tunnelFilter(5, 1, true);
        assertThat(callWith(target, TUNNEL, "10.4.0.1").getStatus()).isEqualTo(200);
        assertThat(callWith(target, TUNNEL, "10.4.0.2").getStatus()).isEqualTo(429);
        assertThat(callWith(target, "/auth/refresh", "10.4.0.3").getStatus()).isEqualTo(200);
    }
}
