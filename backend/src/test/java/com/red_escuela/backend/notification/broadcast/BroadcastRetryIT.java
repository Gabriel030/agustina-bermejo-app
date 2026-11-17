package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.notification.broadcast.specialist.BroadcastEmailDispatchJob;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.specialist.EmailDeliveryChannel;
import com.red_escuela.backend.shared.mail.TransactionalEmail;
import com.red_escuela.backend.shared.mail.TransactionalEmailDeliveryException;
import com.red_escuela.backend.shared.mail.TransactionalEmailGateway;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BroadcastRetryIT extends AbstractBroadcastDispatchIT {

    @Test
    void aMailThatKeepsFailingIsGivenUpAfterThreeAttempts() {
        LocalDate day = LocalDate.of(2043, 3, 10);
        Long mail = queueBroadcast(persistPerson(), com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory.INSTITUTIONAL, day);
        AtomicInteger calls = new AtomicInteger();
        TransactionalEmailGateway failing = (TransactionalEmail email) -> {
            calls.incrementAndGet();
            throw new TransactionalEmailDeliveryException("timeout", null);
        };
        BroadcastEmailDispatchJob job = jobWith(300, 100, 100, recordingChannel(failing));

        at(day, 9, 0);
        job.dispatchPendingBroadcastEmails();
        assertThat(deliveryOf(mail).getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        at(day, 9, 1);
        job.dispatchPendingBroadcastEmails();
        assertThat(deliveryOf(mail).getStatus()).isEqualTo(DeliveryStatus.QUEUED);
        at(day, 9, 2);
        job.dispatchPendingBroadcastEmails();
        assertThat(deliveryOf(mail).getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(deliveryOf(mail).getStatusReason()).isEqualTo("PROVIDER_ERROR");
        at(day, 9, 3);
        job.dispatchPendingBroadcastEmails();

        assertThat(calls).hasValue(EmailDeliveryChannel.MAX_SEND_ATTEMPTS);
    }

    @Test
    void aMailThatFailsTwiceIsSentOnTheThirdAttemptAndFailuresDoNotConsumeTheQuota() {
        LocalDate day = LocalDate.of(2043, 4, 10);
        var recipient = persistPerson();
        Long flaky = queueBroadcast(recipient, com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory.INSTITUTIONAL, day);
        AtomicInteger calls = new AtomicInteger();
        TransactionalEmailGateway flakyGateway = (TransactionalEmail email) -> {
            if (calls.incrementAndGet() <= 2) {
                throw new TransactionalEmailDeliveryException("timeout", null);
            }
            return realGateway.send(email);
        };
        BroadcastEmailDispatchJob job = jobWith(1, 0, 100, recordingChannel(flakyGateway));

        at(day, 9, 0);
        job.dispatchPendingBroadcastEmails();
        at(day, 9, 1);
        job.dispatchPendingBroadcastEmails();
        at(day, 9, 2);
        job.dispatchPendingBroadcastEmails();

        assertThat(deliveryOf(flaky).getStatus()).isEqualTo(DeliveryStatus.SENT_SIMULATED);
        assertThat(deliveryOf(flaky).getAttempts()).isEqualTo(2);
    }
}
