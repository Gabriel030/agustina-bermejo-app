package com.red_escuela.backend.billing.run.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-160")
class FailedInvoiceRetryScheduleTest {
    private static final FailedInvoiceRetrySchedule DEFAULT = new FailedInvoiceRetrySchedule(5, List.of(1, 3, 6, 12, 24));

    @Test
    void eachFailedExecutionWaitsTheConfiguredBackoffOfItsRetry() {
        assertThat(DEFAULT.waitAfterFailedExecution(1)).contains(Duration.ofHours(1));
        assertThat(DEFAULT.waitAfterFailedExecution(2)).contains(Duration.ofHours(3));
        assertThat(DEFAULT.waitAfterFailedExecution(3)).contains(Duration.ofHours(6));
        assertThat(DEFAULT.waitAfterFailedExecution(4)).contains(Duration.ofHours(12));
        assertThat(DEFAULT.waitAfterFailedExecution(5)).contains(Duration.ofHours(24));
    }

    @Test
    void afterTheLastConfiguredRetryThereIsNoMoreWait() {
        assertThat(DEFAULT.waitAfterFailedExecution(6)).isEqualTo(Optional.empty());
        assertThat(DEFAULT.waitAfterFailedExecution(7)).isEmpty();
    }

    @Test
    void withZeroRetriesTheFirstFailureHasNoWait() {
        assertThat(new FailedInvoiceRetrySchedule(0, List.of()).waitAfterFailedExecution(1)).isEmpty();
        assertThat(FailedInvoiceRetrySchedule.withoutRetries().waitAfterFailedExecution(1)).isEmpty();
    }

    @Test
    void aScheduleNeedsABackoffForEveryRetryAndNoNegativeRetries() {
        assertThatThrownBy(() -> new FailedInvoiceRetrySchedule(3, List.of(1, 2))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FailedInvoiceRetrySchedule(-1, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theRetryWindowOpensAtSixAndClosesAtTenPm() {
        assertThat(FailedInvoiceRetryWindow.isOpenAt(LocalTime.of(5, 59))).isFalse();
        assertThat(FailedInvoiceRetryWindow.isOpenAt(LocalTime.of(6, 0))).isTrue();
        assertThat(FailedInvoiceRetryWindow.isOpenAt(LocalTime.of(21, 59))).isTrue();
        assertThat(FailedInvoiceRetryWindow.isOpenAt(LocalTime.of(22, 0))).isFalse();
        assertThat(FailedInvoiceRetryWindow.isOpenAt(LocalTime.of(4, 0))).isFalse();
    }
}
