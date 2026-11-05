package com.red_escuela.backend.notification.reporting.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("e02")
class ReminderPaymentFollowUpTest {

    private static final Instant SENT_AT = Instant.parse("2026-03-10T10:00:00Z");

    @Test
    void aPaymentJustBeforeTheSeventyTwoHoursAreOverFollowsTheMail() {
        assertThat(ReminderPaymentFollowUp.isFollowedByPayment(SENT_AT, List.of(Instant.parse("2026-03-13T09:59:59Z")))).isTrue();
    }

    @Test
    void aPaymentExactlyAtSeventyTwoHoursStillFollowsTheMail() {
        assertThat(ReminderPaymentFollowUp.isFollowedByPayment(SENT_AT, List.of(Instant.parse("2026-03-13T10:00:00Z")))).isTrue();
    }

    @Test
    void aPaymentOneSecondAfterSeventyTwoHoursDoesNotFollowTheMail() {
        assertThat(ReminderPaymentFollowUp.isFollowedByPayment(SENT_AT, List.of(Instant.parse("2026-03-13T10:00:01Z")))).isFalse();
    }

    @Test
    void aPaymentMadeBeforeTheMailWasSentDoesNotFollowIt() {
        assertThat(ReminderPaymentFollowUp.isFollowedByPayment(SENT_AT, List.of(Instant.parse("2026-03-10T09:59:59Z")))).isFalse();
    }

    @Test
    void aPaymentAtTheSameInstantAsTheMailFollowsIt() {
        assertThat(ReminderPaymentFollowUp.isFollowedByPayment(SENT_AT, List.of(SENT_AT))).isTrue();
    }

    @Test
    void anyPaymentInsideTheWindowIsEnoughEvenIfOthersFallOutside() {
        assertThat(ReminderPaymentFollowUp.isFollowedByPayment(SENT_AT,
                List.of(Instant.parse("2026-03-01T10:00:00Z"), Instant.parse("2026-03-11T10:00:00Z"), Instant.parse("2026-04-01T10:00:00Z")))).isTrue();
    }

    @Test
    void withoutPaymentsTheMailIsNotFollowed() {
        assertThat(ReminderPaymentFollowUp.isFollowedByPayment(SENT_AT, List.of())).isFalse();
    }
}
