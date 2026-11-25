package com.red_escuela.backend.config;

import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import com.red_escuela.backend.shared.exception.RequestRateLimitExceededException;
import com.red_escuela.backend.shared.ratelimit.KeyedRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class RequestRateLimitFilter extends OncePerRequestFilter {
    private static final Duration IDLE_WINDOW = Duration.ofMinutes(30);
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD");
    private static final Set<String> IGNORED_METHODS = Set.of("OPTIONS", "TRACE");
    private static final String POST = "POST";
    private static final String STAFF_PROSPECTS_PATH = "/prospects/by-staff";
    private static final String UNSUBSCRIBE_PREFIX = "/public/unsubscribe/";
    private static final String UNSUBSCRIBE_LIMITER_KEY = "/public/unsubscribe";
    private static final String MONITORING_TUNNEL_PATH = "/public/monitoring/tunnel";
    private static final String MONITORING_TUNNEL_GLOBAL_KEY = "monitoring-tunnel-global";

    private final Map<String, KeyedRateLimiter> publicLimiters;
    private final KeyedRateLimiter staffProspectsLimiter;
    private final KeyedRateLimiter userReadLimiter;
    private final KeyedRateLimiter userWriteLimiter;
    private final KeyedRateLimiter monitoringTunnelGlobalLimiter;
    private final boolean monitoringTunnelActive;
    private final JsonErrorResponseWriter errorWriter;

    @Autowired
    public RequestRateLimitFilter(
            JsonErrorResponseWriter errorWriter,
            @Value("${app.rate-limit.max-tracked-keys:50000}") int maxTrackedKeys,
            @Value("${app.rate-limit.refresh.capacity:30}") int refreshCapacity,
            @Value("${app.rate-limit.refresh.refill-per-minute:30}") int refreshRefill,
            @Value("${app.rate-limit.reset-password.capacity:10}") int resetPasswordCapacity,
            @Value("${app.rate-limit.reset-password.refill-per-minute:10}") int resetPasswordRefill,
            @Value("${app.rate-limit.prospects.capacity:10}") int prospectsCapacity,
            @Value("${app.rate-limit.prospects.refill-per-minute:10}") int prospectsRefill,
            @Value("${app.rate-limit.staff-prospects.capacity:30}") int staffProspectsCapacity,
            @Value("${app.rate-limit.staff-prospects.refill-per-minute:30}") int staffProspectsRefill,
            @Value("${app.rate-limit.user-read.capacity:600}") int userReadCapacity,
            @Value("${app.rate-limit.user-read.refill-per-minute:600}") int userReadRefill,
            @Value("${app.rate-limit.user-write.capacity:120}") int userWriteCapacity,
            @Value("${app.rate-limit.user-write.refill-per-minute:120}") int userWriteRefill,
            @Value("${app.rate-limit.monitoring-tunnel-global.capacity:120}") int monitoringTunnelGlobalCapacity,
            @Value("${app.rate-limit.monitoring-tunnel-global.refill-per-minute:120}") int monitoringTunnelGlobalRefill,
            @Value("${sentry.dsn:}") String sentryDsn) {
        this(errorWriter, Clock.systemUTC(), maxTrackedKeys, refreshCapacity, refreshRefill, resetPasswordCapacity,
                resetPasswordRefill, prospectsCapacity, prospectsRefill, staffProspectsCapacity, staffProspectsRefill,
                userReadCapacity, userReadRefill,
                userWriteCapacity, userWriteRefill, monitoringTunnelGlobalCapacity, monitoringTunnelGlobalRefill,
                sentryDsn != null && !sentryDsn.isBlank());
    }

    RequestRateLimitFilter(JsonErrorResponseWriter errorWriter, Clock clock, int maxTrackedKeys, int refreshCapacity,
                           int refreshRefill, int resetPasswordCapacity, int resetPasswordRefill, int prospectsCapacity,
                           int prospectsRefill, int staffProspectsCapacity, int staffProspectsRefill,
                           int userReadCapacity, int userReadRefill, int userWriteCapacity,
                           int userWriteRefill, int monitoringTunnelGlobalCapacity,
                           int monitoringTunnelGlobalRefill, boolean monitoringTunnelActive) {
        this.errorWriter = errorWriter;
        this.monitoringTunnelActive = monitoringTunnelActive;
        this.monitoringTunnelGlobalLimiter = new KeyedRateLimiter(monitoringTunnelGlobalCapacity,
                monitoringTunnelGlobalRefill, 1, IDLE_WINDOW, clock);
        this.publicLimiters = Map.of(
                "/auth/refresh", new KeyedRateLimiter(refreshCapacity, refreshRefill, maxTrackedKeys, IDLE_WINDOW, clock),
                "/auth/reset-password", new KeyedRateLimiter(resetPasswordCapacity, resetPasswordRefill, maxTrackedKeys, IDLE_WINDOW, clock),
                "/auth/activate", new KeyedRateLimiter(resetPasswordCapacity, resetPasswordRefill, maxTrackedKeys, IDLE_WINDOW, clock),
                "/auth/activation-token/status", new KeyedRateLimiter(resetPasswordCapacity, resetPasswordRefill, maxTrackedKeys, IDLE_WINDOW, clock),
                "/prospects", new KeyedRateLimiter(prospectsCapacity, prospectsRefill, maxTrackedKeys, IDLE_WINDOW, clock),
                "/email-change-preview", new KeyedRateLimiter(resetPasswordCapacity, resetPasswordRefill, maxTrackedKeys, IDLE_WINDOW, clock),
                "/approve-email-change", new KeyedRateLimiter(resetPasswordCapacity, resetPasswordRefill, maxTrackedKeys, IDLE_WINDOW, clock),
                "/cancel-email-change", new KeyedRateLimiter(resetPasswordCapacity, resetPasswordRefill, maxTrackedKeys, IDLE_WINDOW, clock),
                UNSUBSCRIBE_LIMITER_KEY, new KeyedRateLimiter(resetPasswordCapacity, resetPasswordRefill, maxTrackedKeys, IDLE_WINDOW, clock),
                MONITORING_TUNNEL_PATH, new KeyedRateLimiter(refreshCapacity, refreshRefill, maxTrackedKeys, IDLE_WINDOW, clock));
        this.staffProspectsLimiter = new KeyedRateLimiter(staffProspectsCapacity, staffProspectsRefill, maxTrackedKeys, IDLE_WINDOW, clock);
        this.userReadLimiter = new KeyedRateLimiter(userReadCapacity, userReadRefill, maxTrackedKeys, IDLE_WINDOW, clock);
        this.userWriteLimiter = new KeyedRateLimiter(userWriteCapacity, userWriteRefill, maxTrackedKeys, IDLE_WINDOW, clock);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        KeyedRateLimiter.Decision decision = decide(request);
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }
        RequestRateLimitExceededException exception = new RequestRateLimitExceededException(decision.retryAfter());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfter().toSeconds()));
        errorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS.value(), ErrorCodeResponse.of(exception));
    }

    @Scheduled(cron = "0 */10 * * * *", zone = "${app.timezone}")
    public void evictIdleClients() {
        allLimiters().forEach(KeyedRateLimiter::evictIdle);
    }

    int trackedKeys() {
        return allLimiters().stream().mapToInt(KeyedRateLimiter::trackedKeys).sum();
    }

    private List<KeyedRateLimiter> allLimiters() {
        return Stream.concat(
                publicLimiters.values().stream(),
                Stream.of(staffProspectsLimiter, userReadLimiter, userWriteLimiter, monitoringTunnelGlobalLimiter)).toList();
    }

    private KeyedRateLimiter.Decision decide(HttpServletRequest request) {
        String method = request.getMethod();
        if (IGNORED_METHODS.contains(method)) {
            return permitted();
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        KeyedRateLimiter publicLimiter = publicLimiterFor(method, path);
        if (publicLimiter != null) {
            KeyedRateLimiter.Decision perIp = publicLimiter.tryConsume(request.getRemoteAddr());
            if (perIp.allowed() && monitoringTunnelActive && POST.equals(method) && MONITORING_TUNNEL_PATH.equals(path)) {
                return monitoringTunnelGlobalLimiter.tryConsume(MONITORING_TUNNEL_GLOBAL_KEY);
            }
            return perIp;
        }
        String user = authenticatedUser();
        if (user == null) {
            return permitted();
        }
        return userLimiter(method, path).tryConsume(user);
    }

    private KeyedRateLimiter publicLimiterFor(String method, String path) {
        if (path.startsWith(UNSUBSCRIBE_PREFIX) && (POST.equals(method) || READ_METHODS.contains(method))) {
            return publicLimiters.get(UNSUBSCRIBE_LIMITER_KEY);
        }
        return POST.equals(method) ? publicLimiters.get(path) : null;
    }

    private KeyedRateLimiter userLimiter(String method, String path) {
        if (POST.equals(method) && STAFF_PROSPECTS_PATH.equals(path)) {
            return staffProspectsLimiter;
        }
        return READ_METHODS.contains(method) ? userReadLimiter : userWriteLimiter;
    }

    private static KeyedRateLimiter.Decision permitted() {
        return new KeyedRateLimiter.Decision(true, Duration.ZERO);
    }

    private static String authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }
}
