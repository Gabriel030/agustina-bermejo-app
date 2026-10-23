package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.service.OverdueReceiptReviewAlerter;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.RejectPaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import org.springframework.modulith.events.IncompleteEventPublications;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OverdueReceiptReviewAlertIT extends AbstractBankTransferReceiptHttpIT {

    static final LocalDate MONDAY = LocalDate.of(2027, 3, 15);
    static final LocalDate TUESDAY = MONDAY.plusDays(1);
    static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
    static final LocalDate THURSDAY = MONDAY.plusDays(3);
    static final LocalDate FRIDAY = MONDAY.plusDays(4);
    static final LocalDate LATER_DUE = LocalDate.of(2027, 5, 31);

    @Autowired
    OverdueReceiptReviewAlerter alerter;
    @Autowired
    ApprovePaymentReceiptUseCase approveUseCase;
    @Autowired
    RejectPaymentReceiptUseCase rejectUseCase;
    @Autowired
    UploadPaymentReceiptUseCase legacyUpload;
    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    IncompleteEventPublications incompleteEventPublications;

    final List<LocalDate> insertedHolidays = new ArrayList<>();
    Person director;
    Person administrative;

    @BeforeEach
    void createRecipients() {
        director = personWithRole("DIRECTOR");
        administrative = personWithRole("ADMINISTRATIVO");
    }

    @AfterEach
    void cleanUpGlobalEffects() {
        insertedHolidays.forEach(date -> jdbc.update("delete from holiday where date = ?", date));
        insertedHolidays.clear();
        jdbc.execute("alter table notification drop constraint if exists ck_test_notifications_blocked");
    }

    private Person personWithRole(String role) {
        Person person = newStaffPerson();
        inTransaction(() -> {
            Person managed = entityManager.find(Person.class, person.getId());
            managed.getRoles().add(roleRepository.findByName(role).orElseThrow());
            return managed.getId();
        });
        return person;
    }

    private void holidayOn(LocalDate date) {
        jdbc.update("insert into holiday (date, description) values (?, 'test holiday')", date);
        insertedHolidays.add(date);
    }

    private void clockAt(LocalDate date, int hour, int minute) {
        MutableClock.INSTANCE.setDateTime(date.atTime(hour, minute));
    }

    private BankTransferReceiptSubmissionResult uploadedAt(LocalDate date, int hour, int minute) {
        clockAt(date, hour, minute);
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", LATER_DUE);
        return submit(command(debtor, List.of(invoice), "80000.00", date, nextOperationNumber()));
    }

    private LocalDateTime alertSentAt(Long receiptId) {
        return jdbc.queryForObject("select review_alert_sent_at from bank_transfer_receipt where id = ?", LocalDateTime.class, receiptId);
    }

    private long overdueNotificationsOf(Person person, Long receiptId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(person.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.PAYMENT_RECEIPT_REVIEW_OVERDUE && receiptId.equals(notification.getRelatedEntityId()))
                .count();
    }

    private int alertOn(LocalDate date) {
        clockAt(date, 9, 0);
        return alerter.alertOverdueReviews();
    }

    @Test
    @Tag("D-268")
    @Tag("D-154")
    void mondayUploadAlertsTheDirectorOnThursdayAndTheInvoiceStaysProtected() {
        BankTransferReceiptSubmissionResult receipt = uploadedAt(MONDAY, 10, 0);
        Long invoiceId = receipt.items().get(0).invoiceId();

        assertThat(alertOn(WEDNESDAY)).isZero();
        assertThat(alertSentAt(receipt.receiptId())).isNull();
        assertThat(alertOn(THURSDAY)).isEqualTo(1);

        assertThat(alertSentAt(receipt.receiptId())).isEqualTo(THURSDAY.atTime(9, 0));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(overdueNotificationsOf(director, receipt.receiptId())).isEqualTo(1));
        assertThat(overdueNotificationsOf(administrative, receipt.receiptId())).isZero();
        assertThat(paymentStatusOfInvoice(invoiceId)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(jdbc.queryForObject("select status from bank_transfer_receipt where id = ?", String.class, receipt.receiptId())).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @Tag("D-321")
    @Tag("D-268")
    void aHolidayOnTuesdayDelaysTheAlertFromThursdayToFriday() {
        holidayOn(TUESDAY);
        BankTransferReceiptSubmissionResult receipt = uploadedAt(MONDAY, 10, 0);

        assertThat(alertOn(THURSDAY)).isZero();
        assertThat(alertSentAt(receipt.receiptId())).isNull();
        assertThat(alertOn(FRIDAY)).isEqualTo(1);

        assertThat(alertSentAt(receipt.receiptId())).isEqualTo(FRIDAY.atTime(9, 0));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(overdueNotificationsOf(director, receipt.receiptId())).isEqualTo(1));
    }

    @Test
    @Tag("D-268")
    @Tag("D-321")
    void aUploadAtTheLastMinuteOfTheDayCountsAsThatDayAndNotAsTheNext() {
        BankTransferReceiptSubmissionResult receipt = uploadedAt(MONDAY, 23, 59);

        clockAt(WEDNESDAY, 23, 59);
        assertThat(alerter.alertOverdueReviews()).isZero();
        clockAt(THURSDAY, 0, 0);
        assertThat(alerter.alertOverdueReviews()).isEqualTo(1);

        assertThat(alertSentAt(receipt.receiptId())).isEqualTo(THURSDAY.atTime(0, 0));
    }

    @Test
    @Tag("D-268")
    void aSecondRunOfTheJobDoesNotAlertAgain() {
        BankTransferReceiptSubmissionResult receipt = uploadedAt(MONDAY, 10, 0);

        assertThat(alertOn(THURSDAY)).isEqualTo(1);
        LocalDateTime firstAlert = alertSentAt(receipt.receiptId());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(overdueNotificationsOf(director, receipt.receiptId())).isEqualTo(1));
        assertThat(alertOn(FRIDAY)).isZero();

        assertThat(alertSentAt(receipt.receiptId())).isEqualTo(firstAlert);
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(overdueNotificationsOf(director, receipt.receiptId())).isEqualTo(1));
    }

    @Test
    @Tag("D-268")
    void anApprovedReceiptIsNotAlerted() {
        BankTransferReceiptSubmissionResult receipt = uploadedAt(MONDAY, 10, 0);
        clockAt(TUESDAY, 10, 0);
        approveUseCase.approve(receipt.receiptId(), director, PaymentVerification.none());

        assertThat(alertOn(THURSDAY)).isZero();

        assertThat(alertSentAt(receipt.receiptId())).isNull();
    }

    @Test
    @Tag("D-268")
    void aRejectedReceiptIsNotAlerted() {
        BankTransferReceiptSubmissionResult receipt = uploadedAt(MONDAY, 10, 0);
        clockAt(TUESDAY, 10, 0);
        rejectUseCase.reject(receipt.receiptId(), "No coincide", director);

        assertThat(alertOn(THURSDAY)).isZero();

        assertThat(alertSentAt(receipt.receiptId())).isNull();
    }

    @Test
    @Tag("D-268")
    @Tag("D-260")
    void aLegacyCourseReceiptWithoutPaymentIsAlertedToo() {
        clockAt(MONDAY, 10, 0);
        Debtor debtor = debtor();
        Long invoice = invoiceOf(debtor, "80000.00", LATER_DUE);
        Long receiptId = legacyUpload.upload(invoice, debtor.studentId(), new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf",
                "%PDF-1.4 legado".getBytes())).getId();
        assertThat(jdbc.queryForObject("select payment_id from bank_transfer_receipt where id = ?", Long.class, receiptId)).isNull();

        assertThat(alertOn(THURSDAY)).isEqualTo(1);

        assertThat(alertSentAt(receiptId)).isNotNull();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(overdueNotificationsOf(director, receiptId)).isEqualTo(1));
    }

    @Test
    @Tag("D-268")
    @Tag("D-321")
    void eachReceiptIsCountedFromItsOwnUploadDate() {
        BankTransferReceiptSubmissionResult old = uploadedAt(MONDAY, 10, 0);
        BankTransferReceiptSubmissionResult recent = uploadedAt(WEDNESDAY, 10, 0);

        assertThat(alertOn(THURSDAY)).isEqualTo(1);

        assertThat(alertSentAt(old.receiptId())).isNotNull();
        assertThat(alertSentAt(recent.receiptId())).isNull();
        assertThat(alertOn(LocalDate.of(2027, 3, 22))).isEqualTo(1);
        assertThat(alertSentAt(recent.receiptId())).isNotNull();
    }

    @Test
    @Tag("D-268")
    void aReceiptPendingForWeeksIsAlertedOnceOnTheFirstRun() {
        BankTransferReceiptSubmissionResult receipt = uploadedAt(MONDAY, 10, 0);

        assertThat(alertOn(LocalDate.of(2027, 4, 14))).isEqualTo(1);
        assertThat(alertOn(LocalDate.of(2027, 4, 15))).isZero();

        assertThat(alertSentAt(receipt.receiptId())).isEqualTo(LocalDate.of(2027, 4, 14).atTime(9, 0));
    }

    @Test
    @Tag("D-183")
    @Tag("D-184")
    @Tag("D-268")
    void aFailingNotificationDoesNotBreakTheJobAndIsRetriedWithoutDuplicates() {
        BankTransferReceiptSubmissionResult receipt = uploadedAt(MONDAY, 10, 0);
        Long receiptId = receipt.receiptId();
        jdbc.execute("alter table notification add constraint ck_test_notifications_blocked check (false) not valid");

        int alerted;
        try {
            alerted = alertOn(THURSDAY);
            assertThat(alerted).isEqualTo(1);
            await().atMost(Duration.ofSeconds(20)).until(() -> incompletePublicationsOf(receiptId) == 1);
        } finally {
            jdbc.execute("alter table notification drop constraint if exists ck_test_notifications_blocked");
        }

        assertThat(alertSentAt(receiptId)).isNotNull();
        assertThat(overdueNotificationsOf(director, receiptId)).isZero();
        incompleteEventPublications.resubmitIncompletePublications(publication -> publication.getEvent() instanceof com.red_escuela.backend.billing.events.PaymentReceiptReviewOverdueEvent event
                && receiptId.equals(event.receiptId()));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(overdueNotificationsOf(director, receiptId)).isEqualTo(1));
        assertThat(alertOn(FRIDAY)).isZero();
    }

    private long incompletePublicationsOf(Long receiptId) {
        return jdbc.queryForObject("select count(*) from event_publication where completion_date is null and serialized_event like ?", Long.class,
                "%\"receiptId\":" + receiptId + ",%");
    }
}
