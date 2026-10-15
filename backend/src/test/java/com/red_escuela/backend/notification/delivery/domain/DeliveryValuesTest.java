package com.red_escuela.backend.notification.delivery.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("c08")
class DeliveryValuesTest {
    private static final LocalDate DAY = LocalDate.of(2026, 3, 9);

    private static DeliveryEnvelope envelope(String dedupKey, String kind, int priority, Long recipient, String relatedType, LocalDate scheduledFor) {
        return new DeliveryEnvelope(dedupKey, DeliveryCategory.COLLECTION_REMINDER, kind, priority, recipient, relatedType, 5L, scheduledFor);
    }

    @Test
    void anEnvelopeAtTheLimitsOfEveryColumnIsAccepted() {
        assertThatCode(() -> envelope("k".repeat(120), "x".repeat(64), 1, 1L, "t".repeat(60), DAY)).doesNotThrowAnyException();
    }

    @Test
    void anEnvelopeWithoutRelatedEntityIsAccepted() {
        assertThatCode(() -> envelope("k", "x", 1, 1L, null, DAY)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void aBlankDedupKeyIsRejected(String dedupKey) {
        assertThatThrownBy(() -> envelope(dedupKey, "x", 1, 1L, null, DAY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDedupKeyOverTheColumnLengthIsRejected() {
        assertThatThrownBy(() -> envelope("k".repeat(121), "x", 1, 1L, null, DAY)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void aBlankKindIsRejected(String kind) {
        assertThatThrownBy(() -> envelope("k", kind, 1, 1L, null, DAY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aKindOverTheColumnLengthIsRejected() {
        assertThatThrownBy(() -> envelope("k", "x".repeat(65), 1, 1L, null, DAY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPriorityBelowTheHighestIsRejected() {
        assertThatThrownBy(() -> envelope("k", "x", 0, 1L, null, DAY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aBlankOrTooLongRelatedEntityTypeIsRejected() {
        assertThatThrownBy(() -> envelope("k", "x", 1, 1L, " ", DAY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> envelope("k", "x", 1, 1L, "t".repeat(61), DAY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEnvelopeNeedsRecipientDayAndCategory() {
        assertThatThrownBy(() -> envelope("k", "x", 1, null, null, DAY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> envelope("k", "x", 1, 1L, null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DeliveryEnvelope("k", null, "x", 1, 1L, null, 5L, DAY)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void anEmailSubjectAtTheColumnLengthIsAccepted() {
        assertThat(DeliveryContent.forEmail("s".repeat(300), "texto", null).subject()).hasSize(300);
    }

    @Test
    void anEmailSubjectOverTheColumnLengthIsRejected() {
        assertThatThrownBy(() -> DeliveryContent.forEmail("s".repeat(301), "texto", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEmailWithoutSubjectIsRejected() {
        assertThatThrownBy(() -> DeliveryContent.forEmail(null, "texto", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeliveryContent.forEmail("  ", "texto", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void contentWithoutPlainTextIsRejected(String text) {
        assertThatThrownBy(() -> DeliveryContent.forEmail("asunto", text, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeliveryContent.forInApp(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inAppContentHasNoSubjectOrHtml() {
        DeliveryContent content = DeliveryContent.forInApp("Mensaje");

        assertThat(content.subject()).isNull();
        assertThat(content.bodyHtml()).isNull();
        assertThat(content.bodyText()).isEqualTo("Mensaje");
    }

    @Test
    void aStatusReasonAtTheColumnLengthIsAccepted() {
        assertThat(DeliveryStatusReason.require("R".repeat(60))).hasSize(60);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void aBlankStatusReasonIsRejected(String reason) {
        assertThatThrownBy(() -> DeliveryStatusReason.require(reason)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aStatusReasonOverTheColumnLengthIsRejected() {
        assertThatThrownBy(() -> DeliveryStatusReason.require("R".repeat(61))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(DeliveryStatus.class)
    void onlyTheClosedWithoutSendingStatesAndDeferredRequireAReason(DeliveryStatus status) {
        boolean expected = status == DeliveryStatus.SUPPRESSED || status == DeliveryStatus.DEFERRED
                || status == DeliveryStatus.DISCARDED || status == DeliveryStatus.FAILED;

        assertThat(status.requiresReason()).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(DeliveryStatus.class)
    void onlyQueuedAndDeferredWaitForDispatch(DeliveryStatus status) {
        boolean expected = status == DeliveryStatus.QUEUED || status == DeliveryStatus.DEFERRED;

        assertThat(status.isWaitingForDispatch()).isEqualTo(expected);
    }
}
