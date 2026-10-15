package com.red_escuela.backend.notification.delivery.entity;

import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.exception.IllegalNotificationDeliveryStatusTransitionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("c08")
class NotificationDeliveryTest {
    private static final Instant NOW = Instant.parse("2026-03-09T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-03-09T12:30:00Z");
    private static final DeliveryEnvelope ENVELOPE = new DeliveryEnvelope("reminder:1:2026-03-09", DeliveryCategory.COLLECTION_REMINDER,
            "BEFORE_DUE_REMINDER", 3, 7L, "Invoice", 11L, LocalDate.of(2026, 3, 9));
    private static final DeliveryContent EMAIL = DeliveryContent.forEmail("Asunto", "Texto", "<p>Texto</p>");

    @Test
    void aQueuedEmailCopiesTheEnvelopeAndTheContent() {
        NotificationDelivery delivery = NotificationDelivery.queuedEmail(ENVELOPE, EMAIL, NOW);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        assertThat(delivery.getChannel()).isEqualTo(DeliveryChannel.EMAIL);
        assertThat(delivery.getDedupKey()).isEqualTo("reminder:1:2026-03-09");
        assertThat(delivery.getCategory()).isEqualTo(DeliveryCategory.COLLECTION_REMINDER);
        assertThat(delivery.getKind()).isEqualTo("BEFORE_DUE_REMINDER");
        assertThat(delivery.getPriority()).isEqualTo(3);
        assertThat(delivery.getRecipientPersonId()).isEqualTo(7L);
        assertThat(delivery.getRelatedEntityType()).isEqualTo("Invoice");
        assertThat(delivery.getRelatedEntityId()).isEqualTo(11L);
        assertThat(delivery.getScheduledFor()).isEqualTo(LocalDate.of(2026, 3, 9));
        assertThat(delivery.getSubject()).isEqualTo("Asunto");
        assertThat(delivery.getBodyText()).isEqualTo("Texto");
        assertThat(delivery.getBodyHtml()).isEqualTo("<p>Texto</p>");
        assertThat(delivery.getCreatedAt()).isEqualTo(NOW);
        assertThat(delivery.getAttempts()).isZero();
        assertThat(delivery.getStatusReason()).isNull();
        assertThat(delivery.getSentAt()).isNull();
    }

    @Test
    void aSuppressedEmailKeepsItsReasonAndContent() {
        NotificationDelivery delivery = NotificationDelivery.suppressedEmail(ENVELOPE, EMAIL, "OPTED_OUT", NOW);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SUPPRESSED);
        assertThat(delivery.getStatusReason()).isEqualTo("OPTED_OUT");
        assertThat(delivery.getChannel()).isEqualTo(DeliveryChannel.EMAIL);
        assertThat(delivery.getSubject()).isEqualTo("Asunto");
    }

    @Test
    void anInAppDeliveryIsBornSentWithoutSubject() {
        NotificationDelivery delivery = NotificationDelivery.deliveredInApp(ENVELOPE, DeliveryContent.forInApp("Mensaje"), NOW);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(delivery.getChannel()).isEqualTo(DeliveryChannel.IN_APP);
        assertThat(delivery.getSentAt()).isEqualTo(NOW);
        assertThat(delivery.getSubject()).isNull();
        assertThat(delivery.getBodyText()).isEqualTo("Mensaje");
    }

    @Test
    void sendingThroughTheSimulatorRecordsTheMessageIdAndTheInstant() {
        NotificationDelivery delivery = NotificationDelivery.queuedEmail(ENVELOPE, EMAIL, NOW);

        delivery.markSent("mock-1", true, LATER);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SENT_SIMULATED);
        assertThat(delivery.getProviderMessageId()).isEqualTo("mock-1");
        assertThat(delivery.getSentAt()).isEqualTo(LATER);
    }

    @Test
    void sendingThroughARealProviderEndsInSent() {
        NotificationDelivery delivery = NotificationDelivery.queuedEmail(ENVELOPE, EMAIL, NOW);

        delivery.markSent("brevo-9", false, LATER);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void aFailedAttemptBelowTheLimitKeepsTheDeliveryQueuedWithTheLastReason() {
        NotificationDelivery delivery = NotificationDelivery.queuedEmail(ENVELOPE, EMAIL, NOW);

        delivery.recordFailedAttempt("PROVIDER_ERROR", 3);
        delivery.recordFailedAttempt("PROVIDER_ERROR", 3);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        assertThat(delivery.getAttempts()).isEqualTo(2);
        assertThat(delivery.getStatusReason()).isEqualTo("PROVIDER_ERROR");
    }

    @Test
    void theAttemptThatReachesTheLimitFailsTheDelivery() {
        NotificationDelivery delivery = NotificationDelivery.queuedEmail(ENVELOPE, EMAIL, NOW);

        delivery.recordFailedAttempt("PROVIDER_ERROR", 3);
        delivery.recordFailedAttempt("PROVIDER_ERROR", 3);
        delivery.recordFailedAttempt("PROVIDER_ERROR", 3);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(3);
        assertThat(delivery.getStatusReason()).isEqualTo("PROVIDER_ERROR");
    }

    @Test
    void aDeliverySentAfterAFailedAttemptDropsTheOldReason() {
        NotificationDelivery delivery = NotificationDelivery.queuedEmail(ENVELOPE, EMAIL, NOW);
        delivery.recordFailedAttempt("PROVIDER_ERROR", 3);

        delivery.markSent("mock-2", true, LATER);

        assertThat(delivery.getStatusReason()).isNull();
        assertThat(delivery.getAttempts()).isEqualTo(1);
    }

    @Test
    void aFailedAttemptOnAFinishedDeliveryIsRejectedWithoutCountingIt() {
        NotificationDelivery delivery = NotificationDelivery.queuedEmail(ENVELOPE, EMAIL, NOW);
        delivery.markSent("mock-3", true, LATER);

        assertThatThrownBy(() -> delivery.recordFailedAttempt("PROVIDER_ERROR", 3)).isInstanceOf(IllegalNotificationDeliveryStatusTransitionException.class);
        assertThat(delivery.getAttempts()).isZero();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SENT_SIMULATED);
    }

    @Test
    void aDeferredDeliveryKeepsTheInstantItWaitsFor() {
        NotificationDelivery delivery = NotificationDelivery.queuedEmail(ENVELOPE, EMAIL, NOW);

        delivery.defer("DAILY_CAP", LATER);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DEFERRED);
        assertThat(delivery.getStatusReason()).isEqualTo("DAILY_CAP");
        assertThat(delivery.getNotBefore()).isEqualTo(LATER);
    }

    @Test
    void deferringWithoutTheInstantItWaitsForIsRejectedAndKeepsTheStatus() {
        NotificationDelivery delivery = NotificationDelivery.queuedEmail(ENVELOPE, EMAIL, NOW);

        assertThatThrownBy(() -> delivery.defer("DAILY_CAP", null)).isInstanceOf(NullPointerException.class);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.QUEUED);
    }

    @Test
    void aSuppressedDeliveryCannotBeSent() {
        NotificationDelivery delivery = NotificationDelivery.suppressedEmail(ENVELOPE, EMAIL, "OPTED_OUT", NOW);

        assertThatThrownBy(() -> delivery.markSent("mock-4", true, LATER)).isInstanceOf(IllegalNotificationDeliveryStatusTransitionException.class);
        assertThat(delivery.getProviderMessageId()).isNull();
        assertThat(delivery.getSentAt()).isNull();
    }

    @Test
    void aStatusReasonLongerThanTheColumnIsRejected() {
        NotificationDelivery delivery = NotificationDelivery.queuedEmail(ENVELOPE, EMAIL, NOW);

        assertThatThrownBy(() -> delivery.suppress("X".repeat(61))).isInstanceOf(IllegalArgumentException.class);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.QUEUED);
    }
}
