package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.notification.broadcast.specialist.BroadcastEmailDispatchJob;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Queues and dispatches five hundred mails over three simulated days")
class BroadcastDispatchOverThreeDaysIT extends AbstractBroadcastDispatchIT {

    @Test
    void fiveHundredMailsWithALimitOfTwoHundredGoOutTwoHundredTwoHundredAndOneHundredWithoutRepeats() {
        LocalDate first = LocalDate.of(2041, 3, 10);
        var recipient = persistPerson();
        queueBroadcasts(recipient, 500, first);
        BroadcastEmailDispatchJob job = jobWith(200, 0, 100, recordingChannel(realGateway));

        for (int day = 0; day < 3; day++) {
            at(first.plusDays(day), 9, 0);
            int sentInTheDay = 0;
            int sentInTheRun;
            do {
                sentInTheRun = job.dispatchPendingBroadcastEmails();
                sentInTheDay += sentInTheRun;
            } while (sentInTheRun > 0);
            assertThat(sentInTheDay).isEqualTo(day < 2 ? 200 : 100);
        }

        assertThat(sentOn(first, DeliveryCategory.BROADCAST)).isEqualTo(200);
        assertThat(sentOn(first.plusDays(1), DeliveryCategory.BROADCAST)).isEqualTo(200);
        assertThat(sentOn(first.plusDays(2), DeliveryCategory.BROADCAST)).isEqualTo(100);
        assertThat(sentSubjects).hasSize(500).doesNotHaveDuplicates();
        assertThat(new HashSet<>(sentSubjects)).hasSize(500);
        assertThat(count("status = 'QUEUED'")).isZero();
        assertThat(count("attempts > 0")).isZero();
    }

    @Test
    void nothingGoesOutBeforeOrAfterTheWindow() {
        LocalDate day = LocalDate.of(2041, 4, 10);
        queueBroadcasts(persistPerson(), 5, day);
        BroadcastEmailDispatchJob job = jobWith(200, 0, 100, recordingChannel(realGateway));

        at(day, 8, 59);
        assertThat(job.dispatchPendingBroadcastEmails()).isZero();
        at(day, 20, 0);
        assertThat(job.dispatchPendingBroadcastEmails()).isZero();
        at(day, 19, 59);
        assertThat(job.dispatchPendingBroadcastEmails()).isEqualTo(5);
    }
}
