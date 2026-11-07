package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.billing.events.DraftInvoicesAwaitingReviewEvent;
import com.red_escuela.backend.notification.domain.BillingOperationNotification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-452")
class DraftInvoicesAwaitingReviewNotificationListenerTest {
    private final ProcessedEffectGuard effectGuard = mock(ProcessedEffectGuard.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final DraftInvoicesAwaitingReviewNotificationListener listener = new DraftInvoicesAwaitingReviewNotificationListener(effectGuard, publisher);
    private final DraftInvoicesAwaitingReviewEvent event = DraftInvoicesAwaitingReviewEvent.pendingOn(9L, LocalDate.of(2043, 3, 1), LocalDate.of(2043, 3, 5), 12);

    @Test
    void firstDeliveryNotifiesTheStaffWithThePendingCountAndThePeriod() {
        when(effectGuard.runEffectOnce(eq(event), eq(DraftInvoicesAwaitingReviewNotificationListener.STAFF_HANDLER), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        });

        listener.notifyStaff(event);

        ArgumentCaptor<BillingOperationNotification> notification = ArgumentCaptor.forClass(BillingOperationNotification.class);
        verify(publisher).publishEvent(notification.capture());
        assertThat(notification.getValue().type()).isEqualTo(NotificationType.DRAFT_INVOICES_AWAITING_REVIEW);
        assertThat(notification.getValue().message()).contains("12").contains("2043-03");
        assertThat(notification.getValue().relatedEntityId()).isEqualTo(9L);
    }

    @Test
    void redeliveredEventDoesNotNotifyAgain() {
        when(effectGuard.runEffectOnce(eq(event), any(), any(Runnable.class))).thenReturn(false);

        listener.notifyStaff(event);

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void theSameRunAndDayAlwaysProduceTheSameEventIdentityAndAnotherDayDoesNot() {
        DraftInvoicesAwaitingReviewEvent sameDay = DraftInvoicesAwaitingReviewEvent.pendingOn(9L, LocalDate.of(2043, 3, 1), LocalDate.of(2043, 3, 5), 3);
        DraftInvoicesAwaitingReviewEvent nextDay = DraftInvoicesAwaitingReviewEvent.pendingOn(9L, LocalDate.of(2043, 3, 1), LocalDate.of(2043, 3, 6), 12);

        assertThat(sameDay.eventId()).isEqualTo(event.eventId());
        assertThat(nextDay.eventId()).isNotEqualTo(event.eventId());
    }
}
