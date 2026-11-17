package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.notification.broadcast.specialist.BroadcastEmailDispatchJob;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BroadcastDispatchSharesQuotaWithTransactionalIT extends AbstractBroadcastDispatchIT {

    @Test
    void transactionalMailsAboveTheReserveReduceTheBroadcastQuotaOfThatDay() {
        LocalDate day = LocalDate.of(2042, 3, 10);
        var transactionalRecipient = persistPerson();
        queueBroadcasts(persistPerson(), 400, day);
        at(day, 8, 0);
        sendTransactional(transactionalRecipient, DeliveryCategory.COLLECTION_REMINDER, 40, day);
        sendTransactional(transactionalRecipient, DeliveryCategory.ANNOUNCEMENT, 110, day);
        BroadcastEmailDispatchJob job = jobWith(300, 100, 100, recordingChannel(realGateway));

        at(day, 9, 0);
        int sent = drain(job);

        assertThat(sent).isEqualTo(150);
        assertThat(sentOn(day, DeliveryCategory.BROADCAST)).isEqualTo(150);
    }

    @Test
    void transactionalMailsBelowTheReserveDoNotReduceTheBroadcastQuota() {
        LocalDate day = LocalDate.of(2042, 4, 10);
        queueBroadcasts(persistPerson(), 400, day);
        at(day, 8, 0);
        sendTransactional(persistPerson(), DeliveryCategory.ANNOUNCEMENT, 30, day);
        BroadcastEmailDispatchJob job = jobWith(300, 100, 100, recordingChannel(realGateway));

        at(day, 9, 0);

        assertThat(drain(job)).isEqualTo(200);
    }

    @Test
    void theQuotaIsCountedByCalendarDay() {
        LocalDate day = LocalDate.of(2042, 5, 10);
        queueBroadcasts(persistPerson(), 400, day);
        at(day, 8, 0);
        sendTransactional(persistPerson(), DeliveryCategory.ANNOUNCEMENT, 150, day);
        BroadcastEmailDispatchJob job = jobWith(300, 100, 100, recordingChannel(realGateway));
        at(day, 9, 0);
        assertThat(drain(job)).isEqualTo(150);

        at(day.plusDays(1), 9, 0);

        assertThat(drain(job)).isEqualTo(200);
    }

    private int drain(BroadcastEmailDispatchJob job) {
        int total = 0;
        int sent;
        do {
            sent = job.dispatchPendingBroadcastEmails();
            total += sent;
        } while (sent > 0);
        return total;
    }
}
