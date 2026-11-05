package com.red_escuela.backend.testsupport;

import org.awaitility.core.ThrowingRunnable;
import org.springframework.test.context.transaction.TestTransaction;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.awaitility.Awaitility.await;

public final class AsyncNotifications {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private AsyncNotifications() {
    }

    public static void awaitAsserted(ThrowingRunnable assertion) {
        await().atMost(TIMEOUT).untilAsserted(assertion);
    }

    public static void awaitPublicationsOfDrained(JdbcTemplate jdbc, String eventTypePattern) {
        await().atMost(TIMEOUT).until(() -> jdbc.queryForObject(
                "select count(*) from event_publication where completion_date is null and event_type like ?", Long.class, eventTypePattern) == 0L);
    }

    public static void commitTestTransaction() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }
}
