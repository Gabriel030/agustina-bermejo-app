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

    private final Map<String, KeyedRateLimiter> publicLimiters;
    private final KeyedRateLimiter userReadLimiter;
    private final KeyedRateLimiter userWriteLimiter;
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
            @Value("${app.rate-limit.user-read.capacity:600}") int userReadCapacity,
            @Value("${app.rate-limit.user-read.refill-per-minute:600}") int userReadRefill,
            @Value("${app.rate-limit.user-write.capacity:120}") int userWriteCapacity,
            @Value("${app.rate-limit.user-write.refill-per-minute:120}") int userWriteRefill) {
        this(errorWriter, Clock.systemUTC(), maxTrackedKeys, refreshCapacity, refreshRefill, resetPasswordCapacity,
                resetPasswordRefill, prospectsCapacity, prospectsRefill, userReadCapacity, userReadRefill,
                userWriteCapacity, userWriteRefill);
    }

    RequestRateLimitFilter(JsonErrorResponseWriter errorWriter, Clock clock, int maxTrackedKeys, int refreshCapacity,
                           int refreshRefill, int resetPasswordCapacity, int resetPasswordRefill, int prospectsCapacity,
                           int prospectsRefill, int userReadCapacity, int userReadRefill, int userWriteCapacity,
                           int userWriteRefill) {
        this.errorWriter = errorWriter;
        this.publicLimiters = Map.of(
                "/auth/refresh", new KeyedRateLimiter(refreshCapacity, refreshRefill, maxTrackedKeys, IDLE_WINDOW, clock),
                "/auth/reset-password", new KeyedRateLimiter(resetPasswordCapacity, resetPasswordRefill, maxTrackedKeys, IDLE_WINDOW, clock),
                "/prospects", new KeyedRateLimiter(prospectsCapacity, prospectsRefill, maxTrackedKeys, IDLE_WINDOW, clock));
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
                Stream.of(userReadLimiter, userWriteLimiter)).toList();
    }

    private KeyedRateLimiter.Decision decide(HttpServletRequest request) {
        String method = request.getMethod();
        if (IGNORED_METHODS.contains(method)) {
            return permitted();
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        KeyedRateLimiter publicLimiter = POST.equals(method) ? publicLimiters.get(path) : null;
        if (publicLimiter != null) {
            return publicLimiter.tryConsume(request.getRemoteAddr());
        }
        String user = authenticatedUser();
        if (user == null) {
            return permitted();
        }
        KeyedRateLimiter limiter = READ_METHODS.contains(method) ? userReadLimiter : userWriteLimiter;
        return limiter.tryConsume(user);
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
