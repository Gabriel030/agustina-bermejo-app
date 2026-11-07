package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.AnnualUpfrontDeadlineNoticePlannedEvent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-349")
class AnnualUpfrontDeadlineNoticeMessageComposerTest {
    private final AnnualUpfrontDeadlineNoticeMessageComposer composer = new AnnualUpfrontDeadlineNoticeMessageComposer();
    private final AnnualUpfrontDeadlineNoticePlannedEvent event = AnnualUpfrontDeadlineNoticePlannedEvent.plannedFor(7L, 9L, 1L, LocalDateTime.of(2027, 3, 4, 10, 0),
            ars(900000), LocalDate.of(2027, 3, 3));

    @Test
    void theMailStatesTheDeadlineTheTotalAndHowToPayWithoutAmountsInTheSubject() {
        DeliveryContent mail = composer.composeEmail(event);

        assertThat(mail.subject()).isEqualTo(AnnualUpfrontDeadlineNoticeMessageComposer.SUBJECT).doesNotContain("900");
        assertThat(mail.bodyText()).contains("ARS 900.000,00", "04/03/2027 10:00", "sección Pagos", "comprobante", "cuotas mensuales");
    }

    @Test
    void theInAppNoticeStatesTheSameFactsAndHasNoSubject() {
        DeliveryContent inApp = composer.composeInApp(event);

        assertThat(inApp.subject()).isNull();
        assertThat(inApp.bodyText()).contains("ARS 900.000,00", "04/03/2027 10:00", "sección Pagos");
    }

    @Test
    void theEventIdIsTheSameForTheSameRequestSoARepeatedPlanIsTheSameEvent() {
        AnnualUpfrontDeadlineNoticePlannedEvent again = AnnualUpfrontDeadlineNoticePlannedEvent.plannedFor(7L, 9L, 1L, LocalDateTime.of(2027, 3, 4, 10, 0), ars(900000),
                LocalDate.of(2027, 3, 3).plusDays(1));
        AnnualUpfrontDeadlineNoticePlannedEvent other = AnnualUpfrontDeadlineNoticePlannedEvent.plannedFor(8L, 9L, 1L, LocalDateTime.of(2027, 3, 4, 10, 0), ars(900000),
                LocalDate.of(2027, 3, 3));

        assertThat(again.eventId()).isEqualTo(event.eventId());
        assertThat(other.eventId()).isNotEqualTo(event.eventId());
    }
}
