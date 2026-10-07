package com.red_escuela.backend.shared.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void itCopiesRequestIdAndUserIdToTheTaskThread() throws InterruptedException {
        MDC.put(CorrelationIds.REQUEST_ID_KEY, "request-1");
        MDC.put(CorrelationIds.USER_ID_KEY, "7");
        MDC.put("other", "ignored");
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> userId = new AtomicReference<>();
        AtomicReference<String> other = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> {
            requestId.set(MDC.get(CorrelationIds.REQUEST_ID_KEY));
            userId.set(MDC.get(CorrelationIds.USER_ID_KEY));
            other.set(MDC.get("other"));
        });

        Thread worker = new Thread(decorated);
        worker.start();
        worker.join();

        assertThat(requestId.get()).isEqualTo("request-1");
        assertThat(userId.get()).isEqualTo("7");
        assertThat(other.get()).isNull();
    }

    @Test
    void itRestoresThePreviousContextOfTheExecutingThread() {
        MDC.put(CorrelationIds.REQUEST_ID_KEY, "captured");
        Runnable decorated = decorator.decorate(() -> {
        });
        MDC.put(CorrelationIds.REQUEST_ID_KEY, "worker-own");

        decorated.run();

        assertThat(MDC.get(CorrelationIds.REQUEST_ID_KEY)).isEqualTo("worker-own");
    }

    @Test
    void itLeavesAnEmptyContextWhenTheExecutingThreadHadNone() {
        MDC.put(CorrelationIds.REQUEST_ID_KEY, "captured");
        Runnable decorated = decorator.decorate(() -> {
        });
        MDC.clear();

        decorated.run();

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void correlationIdFallsBackToAGeneratedValueWithoutARequest() {
        assertThat(CorrelationIds.current()).isNotBlank();

        MDC.put(CorrelationIds.REQUEST_ID_KEY, "request-9");

        assertThat(CorrelationIds.current()).isEqualTo("request-9");
    }
}
