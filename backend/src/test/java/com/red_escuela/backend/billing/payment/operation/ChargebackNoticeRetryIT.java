package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.events.PaymentChargedBackEvent;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.modulith.events.IncompleteEventPublications;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Tag("D-183")
@Tag("D-184")
@Tag("D-206")
class ChargebackNoticeRetryIT extends AbstractRefundIT {

    @Autowired
    ApprovePaymentReceiptUseCase approveReceipt;
    @Autowired
    IncompleteEventPublications incompleteEventPublications;
    @Autowired
    NotificationRepository notificationRepository;

    private void failChargebackNoticesOf(long paymentId) {
        jdbc.execute("create or replace function fail_chargeback_notice_" + paymentId + "() returns trigger language plpgsql as $$ begin "
                + "if new.related_entity_id = " + paymentId + " and new.type = 'PAYMENT_CHARGED_BACK' then raise exception 'notification channel down'; end if; return new; end $$");
        jdbc.execute("create trigger trg_fail_chargeback_notice_" + paymentId + " before insert on notification for each row execute function fail_chargeback_notice_" + paymentId + "()");
    }

    private void restoreChargebackNoticesOf(long paymentId) {
        jdbc.execute("drop trigger if exists trg_fail_chargeback_notice_" + paymentId + " on notification");
        jdbc.execute("drop function if exists fail_chargeback_notice_" + paymentId + "()");
    }

    private long incompletePublicationsOf(long paymentId) {
        return jdbc.queryForObject("select count(*) from event_publication where completion_date is null and serialized_event ~ ?", Long.class,
                "\"paymentId\":" + paymentId + "[,}]");
    }

    private long noticesOf(Person person, long paymentId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(person.getId()).stream()
                .filter(notification -> Long.valueOf(paymentId).equals(notification.getRelatedEntityId()) && "PAYMENT_CHARGED_BACK".equals(notification.getType().name())).count();
    }

    private boolean isOf(org.springframework.modulith.events.EventPublication publication, long paymentId) {
        return publication.getEvent() instanceof PaymentChargedBackEvent event && event.paymentId().equals(paymentId);
    }

    @Test
    void aFailingChargebackNoticeLeavesTheChargebackInPlaceAndIsRetriedExactlyOnce() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submit(command(debtor, List.of(march), "80000.00", TODAY, nextOperationNumber()));
        approveReceipt.approve(submitted.receiptId(), staff, PaymentVerification.none());
        long paymentId = submitted.paymentId();
        Person director = newStaffPerson();
        String directorToken = tokenOf(director, "DIRECTOR");
        failChargebackNoticesOf(paymentId);
        try {
            int status = mockMvc.perform(post("/payments/{id}/chargebacks", paymentId).header("Authorization", "Bearer " + directorToken).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":{\"amount\":80000.00,\"currency\":\"ARS\"},\"chargedBackOn\":\"2027-03-09\",\"reference\":\"BANCO-1\"}")).andReturn().getResponse().getStatus();
            assertThat(status).isEqualTo(201);

            await().atMost(Duration.ofSeconds(30)).until(() -> incompletePublicationsOf(paymentId) == 1);
            assertThat(jdbc.queryForObject("select status from payment where id = ?", String.class, paymentId)).isEqualTo("CHARGED_BACK");
            assertThat(jdbc.queryForObject("select count(*) from chargeback where payment_id = ?", Long.class, paymentId)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select count(*) from current_account_entry where payment_id = ? and entry_type = 'PAYMENT_REVERSED'", Long.class, paymentId))
                    .isEqualTo(1L);
            assertThat(noticesOf(director, paymentId)).isZero();
        } finally {
            restoreChargebackNoticesOf(paymentId);
        }

        incompleteEventPublications.resubmitIncompletePublications(publication -> isOf(publication, paymentId));

        await().atMost(Duration.ofSeconds(30)).until(() -> incompletePublicationsOf(paymentId) == 0 && noticesOf(director, paymentId) == 1);
        incompleteEventPublications.resubmitIncompletePublications(publication -> isOf(publication, paymentId));
        assertThat(noticesOf(director, paymentId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select status from payment where id = ?", String.class, paymentId)).isEqualTo("CHARGED_BACK");
        invariants.assertAllBillingInvariantsHold();
    }
}
