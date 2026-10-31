package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingQuery;
import com.red_escuela.backend.billing.api.EnrollmentPaymentStatusChangeView;
import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.domain.EnrollmentSuspensionRule;
import com.red_escuela.backend.billing.collections.domain.WithdrawnEnrollmentDebtLine;
import com.red_escuela.backend.billing.collections.repository.OverdueContactQuery;
import com.red_escuela.backend.billing.collections.repository.PostWithdrawalSummaryStore;
import com.red_escuela.backend.billing.collections.repository.ReminderFactsQuery;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-510")
class PendingReceiptProtectionCapIT extends AbstractLateFeeScenarioIT {
    private static final int YEAR = 2038;
    private static final EnrollmentSuspensionRule END_OF_MONTH = new EnrollmentSuspensionRule(1, 0);
    private static final LocalDate CUTOFF = d(YEAR, 5, 31);
    private static final LocalDate INSTALLMENT_DUE = d(YEAR, 5, 24);

    @Autowired
    private EnrollmentPaymentStandingService standingService;
    @Autowired
    private EnrollmentPaymentStandingQuery standingQuery;
    @Autowired
    private PendingReceiptProtectionWindow protectionWindow;
    @Autowired
    private OverdueContactQuery overdueContactQuery;
    @Autowired
    private ReminderFactsQuery reminderFactsQuery;
    @Autowired
    private PostWithdrawalSummaryStore postWithdrawalSummaryStore;

    private final List<LocalDate> insertedHolidays = new ArrayList<>();

    @AfterEach
    void restoreTheCalendarAndTheClock() {
        insertedHolidays.forEach(day -> jdbc.update("delete from holiday where date = ?", day));
        insertedHolidays.clear();
        jdbc.update("update configuration_version set config_values = jsonb_set(config_values, '{pendingReceiptProtectionBusinessDays}', '10') where scope = 'LATE_PAYMENT' and effective_from = ?", d(YEAR, 1, 1));
        MutableClock.INSTANCE.reset();
    }

    private void holidayOn(LocalDate day) {
        jdbc.update("insert into holiday (date, description) values (?, 'test holiday')", day);
        insertedHolidays.add(day);
    }

    private Invoice installmentUnderReviewWithReceiptUploadedOn(LocalDate uploadDate) {
        return installmentDueOnUnderReviewWithReceiptUploadedOn(INSTALLMENT_DUE, uploadDate);
    }

    private void configureProtectionCapOf(int businessDays) {
        publish(0, "0.002", 10, true, true, d(YEAR, 1, 1));
        jdbc.update("update configuration_version set config_values = jsonb_set(config_values, '{pendingReceiptProtectionBusinessDays}', ?::jsonb) "
                + "where scope = 'LATE_PAYMENT' and effective_from = ?", String.valueOf(businessDays), d(YEAR, 1, 1));
    }

    private Invoice installmentDueOnUnderReviewWithReceiptUploadedOn(LocalDate dueDate, LocalDate uploadDate) {
        Invoice installment = pendingInstallmentDueOn(dueDate);
        Timestamp uploadedAt = Timestamp.valueOf(uploadDate.atTime(10, 0));
        Long paymentId = jdbc.queryForObject("insert into payment (student_current_account_id, currency, status, gateway, method_type, amount, amount_currency, "
                + "refunded_amount, refunded_amount_currency, registered_at, started_at, method_details, registered_by_process) "
                + "select a.id, a.currency, 'PENDING', 'MANUAL', 'BANK_TRANSFER', 100000, a.currency, 0, a.currency, ?, ?, "
                + "cast(? as jsonb), 'TEST' from student_current_account a where a.id = ? returning id", Long.class,
                uploadedAt, uploadedAt, "{\"type\":\"BANK_TRANSFER\",\"operationNumber\":\"OP-" + System.nanoTime() + "\",\"transferDate\":\"" + uploadDate + "\"}",
                installment.getStudentCurrentAccountId());
        jdbc.update("insert into payment_checkout_item (payment_id, student_current_account_id, invoice_id, position, outstanding_at_selection, "
                + "outstanding_at_selection_currency, marked_under_review) select ?, a.id, ?, 1, 100000, a.currency, true from student_current_account a where a.id = ?",
                paymentId, installment.getId(), installment.getStudentCurrentAccountId());
        jdbc.update("insert into payment_status_change (payment_id, event, from_status, to_status, actor_process, occurred_at) values (?, 'PAYMENT_STARTED', null, 'PENDING', 'TEST', ?)",
                paymentId, uploadedAt);
        jdbc.update("insert into bank_transfer_receipt (invoice_id, file_path, uploaded_at, status, payment_id) values (?, 'receipt.pdf', ?, 'PENDING_REVIEW', ?)",
                installment.getId(), uploadedAt, paymentId);
        jdbc.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", installment.getId());
        return installment;
    }

    private List<String> eventsAfterTheCutoffRuns() {
        standingAfterTheCutoffRuns();
        return standingQuery.listStatusChangesOfCourseEnrollment(enrollment.getId()).stream().map(EnrollmentPaymentStatusChangeView::event).toList();
    }

    private void standingAfterTheCutoffRuns() {
        MutableClock.INSTANCE.setDate(d(YEAR, 5, 20));
        standingService.evaluateEnrollmentStanding(EnrollmentKind.COURSE, enrollment.getId(), d(YEAR, 5, 20), cutoff -> END_OF_MONTH);
        MutableClock.INSTANCE.setDate(d(YEAR, 6, 1));
        standingService.evaluateEnrollmentStanding(EnrollmentKind.COURSE, enrollment.getId(), d(YEAR, 6, 1), cutoff -> END_OF_MONTH);
    }

    private boolean contactListSeesTheStudentAsOverdue() {
        LocalDate floor = protectionWindow.earliestProtectedUploadDateAsOf(CUTOFF);
        return overdueContactQuery.findOverdueAccountsAsOf(CUTOFF, floor).stream().anyMatch(row -> row.studentId().equals(student.getId()));
    }

    private ReminderFactsQuery.OpenInvoiceRow reminderRowOf(Invoice installment) {
        LocalDate floor = protectionWindow.earliestProtectedUploadDateAsOf(CUTOFF);
        return reminderFactsQuery.findOpenInvoicesByIds(List.of(installment.getId()), CUTOFF, floor).get(0);
    }

    @Test
    void aReceiptPendingForExactlyTenBusinessDaysAtTheCutoffStillProtectsTheInstallment() {
        arrange(YEAR);
        installmentUnderReviewWithReceiptUploadedOn(d(YEAR, 5, 17));

        assertThat(eventsAfterTheCutoffRuns()).doesNotContain("MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
    }

    @Test
    void aReceiptPendingForElevenBusinessDaysAtTheCutoffNoLongerProtectsAndTheStudentIsSuspended() {
        arrange(YEAR);
        installmentUnderReviewWithReceiptUploadedOn(d(YEAR, 5, 14));

        assertThat(eventsAfterTheCutoffRuns()).contains("MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
    }

    @Test
    void aReceiptUploadedOnASaturdayCountsFromTheNextBusinessDay() {
        holidayOn(d(YEAR, 5, 17));
        arrange(YEAR);
        installmentUnderReviewWithReceiptUploadedOn(d(YEAR, 5, 15));

        assertThat(eventsAfterTheCutoffRuns()).doesNotContain("MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
    }

    @Test
    void aHolidayIsNotCountedSoAReceiptOneBusinessDayOlderThanTheBorderStillExpires() {
        holidayOn(d(YEAR, 5, 17));
        arrange(YEAR);
        installmentUnderReviewWithReceiptUploadedOn(d(YEAR, 5, 13));

        assertThat(eventsAfterTheCutoffRuns()).contains("MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
    }

    @Test
    void theCapComesFromTheLatePaymentTermsEffectiveOnTheReferenceDate() {
        arrange(YEAR);
        configureProtectionCapOf(3);
        installmentUnderReviewWithReceiptUploadedOn(d(YEAR, 5, 26));

        assertThat(protectionWindow.earliestProtectedUploadDateAsOf(CUTOFF)).isEqualTo(d(YEAR, 5, 26));
        assertThat(protectionWindow.earliestProtectedUploadDateAsOf(d(YEAR, 6, 1))).isEqualTo(d(YEAR, 5, 27));
    }

    @Test
    void aConfiguredCapOfThreeBusinessDaysStillProtectsAReceiptUploadedOnTimeAtTheCutoff() {
        arrange(YEAR);
        configureProtectionCapOf(3);
        installmentDueOnUnderReviewWithReceiptUploadedOn(d(YEAR, 5, 28), d(YEAR, 5, 26));

        assertThat(eventsAfterTheCutoffRuns()).doesNotContain("MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
    }

    @Test
    void aConfiguredCapOfTwoBusinessDaysExpiresTheSameReceiptAtTheCutoffAndTheStudentIsSuspended() {
        arrange(YEAR);
        configureProtectionCapOf(2);
        installmentDueOnUnderReviewWithReceiptUploadedOn(d(YEAR, 5, 28), d(YEAR, 5, 26));

        assertThat(eventsAfterTheCutoffRuns()).contains("MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
    }

    @Test
    void anExpiredReceiptStopsSuppressingTheReminderAndAProtectingOneStillDoes() {
        arrange(YEAR);
        Invoice protecting = installmentUnderReviewWithReceiptUploadedOn(d(YEAR, 5, 17));
        Invoice expired = installmentUnderReviewWithReceiptUploadedOn(d(YEAR, 5, 14));

        assertThat(reminderRowOf(protecting).receiptUnderReview()).isTrue();
        assertThat(reminderRowOf(protecting).paymentStatus()).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(reminderRowOf(expired).receiptUnderReview()).isFalse();
        assertThat(reminderRowOf(expired).paymentStatus()).isEqualTo("PENDING");
    }

    @Test
    void anExpiredReceiptPutsTheStudentInTheOverdueContactList() {
        arrange(YEAR);
        installmentUnderReviewWithReceiptUploadedOn(d(YEAR, 5, 14));

        assertThat(contactListSeesTheStudentAsOverdue()).isTrue();
    }

    @Test
    void aProtectingReceiptKeepsTheStudentOutOfTheOverdueContactList() {
        arrange(YEAR);
        installmentUnderReviewWithReceiptUploadedOn(d(YEAR, 5, 17));

        assertThat(contactListSeesTheStudentAsOverdue()).isFalse();
    }

    @Test
    void anExpiredReceiptMakesTheWithdrawnDebtAppearInThePostWithdrawalSummary() {
        arrange(YEAR);
        installmentUnderReviewWithReceiptUploadedOn(d(YEAR, 5, 14));
        jdbc.update("update course_enrollment set status = 'WITHDRAWN', withdrawn_by_person_id = ?, withdrawal_effective_date = ? where id = ?",
                person.getId(), d(YEAR, 4, 15), enrollment.getId());
        LocalDate floor = protectionWindow.earliestProtectedUploadDateAsOf(CUTOFF);

        List<WithdrawnEnrollmentDebtLine> lines = postWithdrawalSummaryStore.findPendingDebtLinesOfWithdrawnEnrollments(CUTOFF, CUTOFF, floor);

        assertThat(lines).anyMatch(line -> enrollment.getId().equals(line.courseEnrollmentId()));
    }

    @Test
    void aProtectingReceiptKeepsTheWithdrawnDebtOutOfThePostWithdrawalSummary() {
        arrange(YEAR);
        installmentUnderReviewWithReceiptUploadedOn(d(YEAR, 5, 17));
        jdbc.update("update course_enrollment set status = 'WITHDRAWN', withdrawn_by_person_id = ?, withdrawal_effective_date = ? where id = ?",
                person.getId(), d(YEAR, 4, 15), enrollment.getId());
        LocalDate floor = protectionWindow.earliestProtectedUploadDateAsOf(CUTOFF);

        List<WithdrawnEnrollmentDebtLine> lines = postWithdrawalSummaryStore.findPendingDebtLinesOfWithdrawnEnrollments(CUTOFF, CUTOFF, floor);

        assertThat(lines).noneMatch(line -> enrollment.getId().equals(line.courseEnrollmentId()));
    }
}
