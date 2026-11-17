package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastEmailDispatchJob;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BroadcastOptOutBeforeSendIT extends AbstractBroadcastDispatchIT {

    @Test
    void aPromotionalMailQueuedBeforeTheOptOutIsSuppressedAndNeverSent() {
        LocalDate day = LocalDate.of(2044, 3, 10);
        var leaver = persistPerson();
        var stayer = persistPerson();
        grantConsent(leaver, BroadcastCategory.PROMOTIONAL);
        grantConsent(stayer, BroadcastCategory.PROMOTIONAL);
        Long leaverMail = queueBroadcast(leaver, BroadcastCategory.PROMOTIONAL, day);
        Long stayerMail = queueBroadcast(stayer, BroadcastCategory.PROMOTIONAL, day);
        revokeConsent(leaver, BroadcastCategory.PROMOTIONAL);
        BroadcastEmailDispatchJob job = jobWith(300, 100, 100, recordingChannel(realGateway));

        at(day, 9, 0);
        int attempted = job.dispatchPendingBroadcastEmails();

        assertThat(deliveryOf(leaverMail).getStatus()).isEqualTo(DeliveryStatus.SUPPRESSED);
        assertThat(deliveryOf(leaverMail).getStatusReason()).isEqualTo("OPTED_OUT");
        assertThat(deliveryOf(stayerMail).getStatus()).isEqualTo(DeliveryStatus.SENT_SIMULATED);
        assertThat(sentSubjects).containsExactly(deliveryOf(stayerMail).getSubject());
        assertThat(attempted).isEqualTo(1);
    }

    @Test
    void anInstitutionalMailIsSuppressedWhenThePersonRevokedItAfterQueueing() {
        LocalDate day = LocalDate.of(2044, 4, 10);
        var leaver = persistPerson();
        Long mail = queueBroadcast(leaver, BroadcastCategory.INSTITUTIONAL, day);
        revokeConsent(leaver, BroadcastCategory.INSTITUTIONAL);
        BroadcastEmailDispatchJob job = jobWith(300, 100, 100, recordingChannel(realGateway));

        at(day, 9, 0);
        job.dispatchPendingBroadcastEmails();

        assertThat(deliveryOf(mail).getStatus()).isEqualTo(DeliveryStatus.SUPPRESSED);
        assertThat(sentSubjects).isEmpty();
    }

    @Test
    void aRevokedPromotionalConsentDoesNotSuppressAnInstitutionalMail() {
        LocalDate day = LocalDate.of(2044, 5, 10);
        var person = persistPerson();
        revokeConsent(person, BroadcastCategory.PROMOTIONAL);
        Long mail = queueBroadcast(person, BroadcastCategory.INSTITUTIONAL, day);
        BroadcastEmailDispatchJob job = jobWith(300, 100, 100, recordingChannel(realGateway));

        at(day, 9, 0);
        job.dispatchPendingBroadcastEmails();

        assertThat(deliveryOf(mail).getStatus()).isEqualTo(DeliveryStatus.SENT_SIMULATED);
    }
}
