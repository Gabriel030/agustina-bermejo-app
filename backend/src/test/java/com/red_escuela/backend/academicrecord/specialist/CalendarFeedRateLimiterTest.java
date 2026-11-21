package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.shared.exception.RequestRateLimitExceededException;
import com.red_escuela.backend.shared.time.MutableClock;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.slf4j.LoggerFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalendarFeedRateLimiterTest {

    private final MutableClock clock = MutableClock.INSTANCE;
    private final CalendarFeedRateLimiter limiter = new CalendarFeedRateLimiter(clock, 3, 1, 6, 6, 3, 100);

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void repeatedMissesBlockTheClientWithRetryAfter() {
        limiter.chargeMiss("203.0.113.1");
        limiter.chargeMiss("203.0.113.1");
        limiter.chargeMiss("203.0.113.1");

        assertThatThrownBy(() -> limiter.assertClientIsNotBlocked("203.0.113.1"))
                .isInstanceOfSatisfying(RequestRateLimitExceededException.class,
                        exception -> assertThat(exception.retryAfter()).isGreaterThanOrEqualTo(Duration.ofSeconds(1)));
    }

    @Test
    void aMissDoesNotBlockAnotherClient() {
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.chargeMiss("203.0.113.2");
        }

        assertThatCode(() -> limiter.assertClientIsNotBlocked("203.0.113.3")).doesNotThrowAnyException();
    }

    @Test
    void aSingleMissDoesNotBlockTheClient() {
        limiter.chargeMiss("203.0.113.4");

        assertThatCode(() -> limiter.assertClientIsNotBlocked("203.0.113.4")).doesNotThrowAnyException();
    }

    @Test
    void blockedClientIsReleasedAfterRetryAfter() {
        for (int attempt = 0; attempt < 3; attempt++) {
            limiter.chargeMiss("203.0.113.5");
        }
        assertThatThrownBy(() -> limiter.assertClientIsNotBlocked("203.0.113.5"))
                .isInstanceOf(RequestRateLimitExceededException.class);

        clock.advance(Duration.ofMinutes(2));

        assertThatCode(() -> limiter.assertClientIsNotBlocked("203.0.113.5")).doesNotThrowAnyException();
    }

    @Test
    void tokenIsLimitedIndependentlyOfTheClient() {
        limiter.acquireForToken("hash-a");
        limiter.acquireForToken("hash-a");
        limiter.acquireForToken("hash-a");

        assertThatThrownBy(() -> limiter.acquireForToken("hash-a"))
                .isInstanceOfSatisfying(RequestRateLimitExceededException.class,
                        exception -> assertThat(exception.retryAfter()).isPositive());
        assertThatCode(() -> limiter.acquireForToken("hash-b")).doesNotThrowAnyException();
    }

    @Test
    void ipv6ClientsOfTheSameSlash64ShareTheMissBudget() {
        for (int attempt = 0; attempt < 3; attempt++) {
            limiter.chargeMiss("2001:db8:1:2::1");
        }

        assertThatThrownBy(() -> limiter.assertClientIsNotBlocked("2001:db8:1:2:ffff::9"))
                .isInstanceOf(RequestRateLimitExceededException.class);
    }

    @Test
    void saturatedMissKeysWarnOnlyOncePerInterval() {
        CalendarFeedRateLimiter small = new CalendarFeedRateLimiter(clock, 3, 1, 6, 6, 1, 2);
        Logger logger = (Logger) LoggerFactory.getLogger(CalendarFeedRateLimiter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (int index = 0; index < 10; index++) {
                small.chargeMiss("203.0.113." + (10 + index));
            }
            assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.WARN).hasSize(1);

            clock.advance(Duration.ofMinutes(11));
            small.chargeMiss("203.0.113.99");

            assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.WARN).hasSize(2);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void unsaturatedMissKeysDoNotWarn() {
        Logger logger = (Logger) LoggerFactory.getLogger(CalendarFeedRateLimiter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            limiter.chargeMiss("203.0.113.50");

            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
        }
    }
}
