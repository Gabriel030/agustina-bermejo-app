package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFacts;
import com.red_escuela.backend.billing.collections.domain.DailyReminderDigest;
import com.red_escuela.backend.billing.collections.domain.NonBusinessDays;
import com.red_escuela.backend.billing.collections.domain.ReminderDigestLine;
import com.red_escuela.backend.billing.collections.domain.ReminderDigestMode;
import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFact;
import com.red_escuela.backend.billing.collections.domain.ReminderMessage;
import com.red_escuela.backend.billing.collections.domain.ReminderMessageFormat;
import com.red_escuela.backend.billing.collections.domain.ReminderStudentFacts;
import com.red_escuela.backend.billing.collections.domain.SuppressedReminder;
import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ResolvedReminderSchedule;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.red_escuela.backend.billing.collections.operation.ReminderDigestScenarios.bothChannels;
import static com.red_escuela.backend.billing.collections.operation.ReminderDigestScenarios.enrollmentFee;
import static com.red_escuela.backend.billing.collections.operation.ReminderDigestScenarios.installment;
import static com.red_escuela.backend.billing.collections.operation.ReminderDigestScenarios.installmentOf;
import static com.red_escuela.backend.billing.collections.operation.ReminderDigestScenarios.lateFeeNote;
import static com.red_escuela.backend.billing.collections.operation.ReminderDigestScenarios.notice;
import static com.red_escuela.backend.billing.collections.operation.ReminderDigestScenarios.schedule;
import static com.red_escuela.backend.billing.collections.operation.ReminderDigestScenarios.withOpenCancellation;
import static com.red_escuela.backend.billing.collections.operation.ReminderDigestScenarios.withPaymentStatus;
import static com.red_escuela.backend.billing.collections.operation.ReminderDigestScenarios.withReceiptUnderReview;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyReminderDigestPlannerTest {

    private static final long STUDENT_ID = 7L;

    private static ResolvedReminderSchedule afterDueTheNextDay() {
        return schedule(bothChannels("after-due", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 1));
    }

    private static DailyReminderDigest plan(String date, Map<Long, ResolvedReminderSchedule> schedules, List<ReminderInvoiceFact> facts) {
        return plan(date, schedules, facts, NonBusinessDays.weekendsOnly(), ReminderDigestMode.DAILY_DIGEST, Map.of());
    }

    private static DailyReminderDigest plan(String date, Map<Long, ResolvedReminderSchedule> schedules, List<ReminderInvoiceFact> facts, NonBusinessDays nonBusinessDays,
                                            ReminderDigestMode mode, Map<Currency, NonNegativeMoney> credit) {
        return DailyReminderDigestPlanner.planDailyDigestOf(new ReminderStudentFacts(STUDENT_ID, facts, credit), schedules, LocalDate.parse(date), nonBusinessDays, mode);
    }

    private static Map<Long, ResolvedReminderSchedule> sameScheduleFor(ResolvedReminderSchedule schedule, long... invoiceIds) {
        Map<Long, ResolvedReminderSchedule> schedules = new HashMap<>();
        for (long invoiceId : invoiceIds) {
            schedules.put(invoiceId, schedule);
        }
        return schedules;
    }

    private static List<Long> invoiceIdsOf(ReminderMessage message) {
        return message.lines().stream().map(ReminderDigestLine::invoiceId).toList();
    }

    private static List<Long> highlightedIdsOf(ReminderMessage message) {
        return message.highlightedLines().stream().map(ReminderDigestLine::invoiceId).toList();
    }

    @Test
    @Tag("D-353")
    @Tag("D-403")
    @Tag("D-391")
    void oneDigestPerStudentPerDayWithAllItsInvoices() {
        ReminderInvoiceFact march = installment(1L, "2027-03-11");
        ReminderInvoiceFact fee = enrollmentFee(2L, "2027-03-11");

        DailyReminderDigest digest = plan("2027-03-12", sameScheduleFor(afterDueTheNextDay(), 1L, 2L), List.of(march, fee));

        assertThat(digest.inAppMessages()).hasSize(1);
        assertThat(digest.emailMessages()).hasSize(1);
        assertThat(invoiceIdsOf(digest.inAppMessages().get(0))).containsExactly(1L, 2L);
        assertThat(invoiceIdsOf(digest.emailMessages().get(0))).containsExactly(1L, 2L);
        assertThat(highlightedIdsOf(digest.emailMessages().get(0))).containsExactly(1L, 2L);
        assertThat(digest.hasNothingToSend()).isFalse();
        assertThat(digest.studentId()).isEqualTo(STUDENT_ID);
        assertThat(digest.businessDate()).isEqualTo(LocalDate.parse("2027-03-12"));
    }

    @Test
    @Tag("D-409")
    @Tag("D-353")
    @Tag("D-089")
    @Tag("D-140")
    void digestListsAllDebtInImputationOrderHighlightingTheDaysNotices() {
        ReminderInvoiceFact march = installment(1L, "2027-03-11");
        ReminderInvoiceFact marchLateFee = lateFeeNote(4L, 1L, "2027-03-11");
        ReminderInvoiceFact april = installment(2L, "2027-04-11");
        ReminderInvoiceFact may = installment(3L, "2027-05-11");
        Map<Long, ResolvedReminderSchedule> schedules = sameScheduleFor(schedule(bothChannels("a", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 1)), 1L, 2L, 3L);

        DailyReminderDigest digest = plan("2027-04-12", schedules, List.of(may, marchLateFee, april, march));

        ReminderMessage email = digest.emailMessages().get(0);
        assertThat(invoiceIdsOf(email)).containsExactly(1L, 4L, 2L, 3L);
        assertThat(highlightedIdsOf(email)).containsExactly(2L);
        assertThat(email.format()).isEqualTo(ReminderMessageFormat.DAILY_DIGEST);
    }

    @Test
    @Tag("D-409")
    void aSingleNoticeOnTheOnlyDebtKeepsItsOwnMessage() {
        ReminderInvoiceFact march = installment(1L, "2027-03-11");

        DailyReminderDigest digest = plan("2027-03-12", sameScheduleFor(afterDueTheNextDay(), 1L), List.of(march));

        assertThat(digest.emailMessages().get(0).format()).isEqualTo(ReminderMessageFormat.OWN_NOTICE);
        assertThat(digest.emailMessages().get(0).leadMessageType()).isEqualTo(ReminderMessageType.AFTER_DUE_REMINDER);
        assertThat(digest.inAppMessages().get(0).format()).isEqualTo(ReminderMessageFormat.OWN_NOTICE);
    }

    @Test
    @Tag("D-409")
    void aSingleNoticeWithOtherDebtListedUsesTheDigestFormat() {
        ReminderInvoiceFact march = installment(1L, "2027-03-11");
        ReminderInvoiceFact april = installment(2L, "2027-04-11");

        DailyReminderDigest digest = plan("2027-03-12", Map.of(1L, afterDueTheNextDay(), 2L, schedule()), List.of(march, april));

        assertThat(digest.emailMessages().get(0).format()).isEqualTo(ReminderMessageFormat.DAILY_DIGEST);
        assertThat(invoiceIdsOf(digest.emailMessages().get(0))).containsExactly(1L, 2L);
        assertThat(highlightedIdsOf(digest.emailMessages().get(0))).containsExactly(1L);
    }

    @Test
    @Tag("D-410")
    @Tag("D-409")
    void noticesOfTheSameInvoiceOnTheSameDayAreMergedIntoOneLine() {
        ReminderInvoiceFact march = installment(1L, "2027-03-11");
        ResolvedReminderSchedule twoNotices = schedule(
                bothChannels("after-due", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 1),
                bothChannels("after-issue", ReminderMessageType.AFTER_ISSUE_REMINDER, ReminderMilestone.ISSUANCE, 11));

        DailyReminderDigest digest = plan("2027-03-12", Map.of(1L, twoNotices), List.of(march));

        assertThat(digest.emailMessages()).hasSize(1);
        ReminderMessage email = digest.emailMessages().get(0);
        assertThat(email.lines()).hasSize(1);
        assertThat(email.lines().get(0).notices()).extracting("noticeKey").containsExactlyInAnyOrder("after-due", "after-issue");
        assertThat(email.format()).isEqualTo(ReminderMessageFormat.DAILY_DIGEST);
        assertThat(email.leadMessageType()).isEqualTo(ReminderMessageType.AFTER_DUE_REMINDER);
        assertThat(email.priority()).isEqualTo(2);
    }

    @Test
    @Tag("D-097")
    @Tag("D-353")
    @Tag("D-410")
    void twoCurrenciesTwoBlocksNeverSummed() {
        ReminderInvoiceFact march = installmentOf(1L, "2027-03-11", "100000.00", Currency.ARS);
        ReminderInvoiceFact course = installmentOf(2L, "2027-03-11", "50.00", Currency.USD);

        DailyReminderDigest digest = plan("2027-03-12", sameScheduleFor(afterDueTheNextDay(), 1L, 2L), List.of(march, course));

        assertThat(digest.emailMessages()).hasSize(1);
        var blocks = digest.emailMessages().get(0).blocks();
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).currency()).isEqualTo(Currency.ARS);
        assertThat(blocks.get(0).total().value().amount()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(blocks.get(1).currency()).isEqualTo(Currency.USD);
        assertThat(blocks.get(1).total().value().amount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @Tag("D-139")
    void shortCreditShowsWhatIsMissing() {
        ReminderInvoiceFact march = installmentOf(1L, "2027-03-11", "100000.00", Currency.ARS);
        Map<Currency, NonNegativeMoney> credit = Map.of(Currency.ARS, NonNegativeMoney.of(new BigDecimal("30000.00"), Currency.ARS));

        DailyReminderDigest digest = plan("2027-03-12", sameScheduleFor(afterDueTheNextDay(), 1L), List.of(march), NonBusinessDays.weekendsOnly(),
                ReminderDigestMode.DAILY_DIGEST, credit);

        var block = digest.emailMessages().get(0).blocks().get(0);
        assertThat(block.availableCredit().value().amount()).isEqualByComparingTo(new BigDecimal("30000.00"));
        assertThat(block.hasCreditShortfall()).isTrue();
        assertThat(block.shortfallInvoiceId()).isEqualTo(1L);
        assertThat(block.missingForNext().value().amount()).isEqualByComparingTo(new BigDecimal("70000.00"));
    }

    @Test
    @Tag("D-403")
    @Tag("D-342")
    void saturdayAndMondayOccurrencesShareOneMondayEmail() {
        ReminderInvoiceFact march = installment(1L, "2027-03-11");
        ReminderInvoiceFact fee = enrollmentFee(2L, "2027-03-15");
        Map<Long, ResolvedReminderSchedule> schedules = Map.of(
                1L, schedule(bothChannels("sat", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 2)),
                2L, schedule(bothChannels("mon", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 0)));

        DailyReminderDigest saturday = plan("2027-03-13", schedules, List.of(march, fee));
        DailyReminderDigest monday = plan("2027-03-15", schedules, List.of(march, fee));

        assertThat(saturday.emailMessages()).isEmpty();
        assertThat(saturday.hasNothingToSend()).isFalse();
        assertThat(saturday.inAppMessages()).hasSize(1);
        assertThat(highlightedIdsOf(saturday.inAppMessages().get(0))).containsExactly(1L);
        assertThat(monday.emailMessages()).hasSize(1);
        assertThat(highlightedIdsOf(monday.emailMessages().get(0))).containsExactly(1L, 2L);
        assertThat(monday.inAppMessages()).hasSize(1);
        assertThat(highlightedIdsOf(monday.inAppMessages().get(0))).containsExactly(2L);
    }

    @Test
    @Tag("D-410")
    @Tag("D-139")
    @Tag("D-089")
    void theSuspensionWarningEmailListsOnlyItsInvoicesButMeasuresTheCreditAgainstTheWholeDebt() {
        ReminderInvoiceFact march = installment(1L, "2027-03-11");
        ReminderInvoiceFact april = installment(2L, "2027-03-12");
        Map<Long, ResolvedReminderSchedule> schedules = Map.of(
                1L, schedule(),
                2L, schedule(bothChannels("warning", ReminderMessageType.BEFORE_SUSPENSION_REMINDER, ReminderMilestone.DUE_DATE, 3)));
        Map<Currency, NonNegativeMoney> credit = Map.of(Currency.ARS, NonNegativeMoney.of(new BigDecimal("30000.00"), Currency.ARS));

        DailyReminderDigest digest = plan("2027-03-15", schedules, List.of(march, april), NonBusinessDays.weekendsOnly(), ReminderDigestMode.DAILY_DIGEST, credit);

        ReminderMessage warning = digest.emailMessages().get(0);
        assertThat(invoiceIdsOf(warning)).containsExactly(2L);
        var block = warning.blocks().get(0);
        assertThat(block.shortfallInvoiceId()).isEqualTo(1L);
        assertThat(block.missingForNext().value().amount()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(block.total().value().amount()).isEqualByComparingTo(new BigDecimal("80000.00"));
    }

    @Test
    @Tag("D-410")
    void twoSuspensionWarningsOnTheSameDayShareOneExemptEmail() {
        ReminderInvoiceFact march = installment(1L, "2027-03-11");
        ReminderInvoiceFact fee = enrollmentFee(2L, "2027-03-11");
        Map<Long, ResolvedReminderSchedule> schedules = sameScheduleFor(
                schedule(bothChannels("warning", ReminderMessageType.BEFORE_SUSPENSION_REMINDER, ReminderMilestone.DUE_DATE, 4)), 1L, 2L);

        DailyReminderDigest digest = plan("2027-03-15", schedules, List.of(march, fee));

        assertThat(digest.emailMessages()).hasSize(1);
        assertThat(invoiceIdsOf(digest.emailMessages().get(0))).containsExactly(1L, 2L);
        assertThat(highlightedIdsOf(digest.emailMessages().get(0))).containsExactly(1L, 2L);
    }

    @Test
    @Tag("D-342")
    void aHolidayMovesTheEmailToTheNextBusinessDayButNotTheInApp() {
        ReminderInvoiceFact march = installment(1L, "2027-03-20");
        Map<Long, ResolvedReminderSchedule> schedules = Map.of(1L, schedule(bothChannels("before", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -3)));
        NonBusinessDays holiday = new NonBusinessDays(Set.of(LocalDate.parse("2027-03-17")));

        DailyReminderDigest onTheHoliday = plan("2027-03-17", schedules, List.of(march), holiday, ReminderDigestMode.DAILY_DIGEST, Map.of());
        DailyReminderDigest theDayAfter = plan("2027-03-18", schedules, List.of(march), holiday, ReminderDigestMode.DAILY_DIGEST, Map.of());

        assertThat(onTheHoliday.inAppMessages()).hasSize(1);
        assertThat(onTheHoliday.emailMessages()).isEmpty();
        assertThat(theDayAfter.inAppMessages()).isEmpty();
        assertThat(theDayAfter.emailMessages()).hasSize(1);
    }

    @Test
    void noDigestOnADayWithoutOccurrences() {
        ReminderInvoiceFact march = installment(1L, "2027-03-11");
        ReminderInvoiceFact april = installment(2L, "2027-04-11");

        DailyReminderDigest digest = plan("2027-03-20", sameScheduleFor(afterDueTheNextDay(), 1L, 2L), List.of(march, april));

        assertThat(digest.hasNothingToSend()).isTrue();
        assertThat(digest.suppressed()).isEmpty();
    }

    @Test
    void digestPriorityIsTheHighestOfItsItems() {
        ReminderInvoiceFact beforeDue = installment(1L, "2027-03-17");
        ReminderInvoiceFact overdue = installment(2L, "2027-03-11");
        Map<Long, ResolvedReminderSchedule> schedules = Map.of(
                1L, schedule(bothChannels("before", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -2)),
                2L, schedule(bothChannels("after", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 4)));

        DailyReminderDigest both = plan("2027-03-15", schedules, List.of(beforeDue, overdue));
        DailyReminderDigest onlyBefore = plan("2027-03-15", Map.of(1L, schedules.get(1L), 2L, schedule()), List.of(beforeDue, overdue));

        assertThat(both.emailMessages().get(0).priority()).isEqualTo(2);
        assertThat(both.emailMessages().get(0).leadMessageType()).isEqualTo(ReminderMessageType.AFTER_DUE_REMINDER);
        assertThat(onlyBefore.emailMessages().get(0).priority()).isEqualTo(4);
        assertThat(onlyBefore.emailMessages().get(0).leadMessageType()).isEqualTo(ReminderMessageType.BEFORE_DUE_REMINDER);
    }

    @Test
    @Tag("D-410")
    void theSuspensionWarningGoesOutInItsOwnEmailApartFromTheDigest() {
        ReminderInvoiceFact march = installment(1L, "2027-03-11");
        ReminderInvoiceFact april = installment(2L, "2027-03-12");
        Map<Long, ResolvedReminderSchedule> schedules = Map.of(
                1L, schedule(bothChannels("after", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 4)),
                2L, schedule(bothChannels("warning", ReminderMessageType.BEFORE_SUSPENSION_REMINDER, ReminderMilestone.DUE_DATE, 3)));

        DailyReminderDigest digest = plan("2027-03-15", schedules, List.of(march, april));

        assertThat(digest.emailMessages()).hasSize(2);
        ReminderMessage regular = digest.emailMessages().get(0);
        ReminderMessage warning = digest.emailMessages().get(1);
        assertThat(invoiceIdsOf(regular)).containsExactly(1L, 2L);
        assertThat(highlightedIdsOf(regular)).containsExactly(1L);
        assertThat(regular.leadMessageType()).isEqualTo(ReminderMessageType.AFTER_DUE_REMINDER);
        assertThat(invoiceIdsOf(warning)).containsExactly(2L);
        assertThat(warning.leadMessageType()).isEqualTo(ReminderMessageType.BEFORE_SUSPENSION_REMINDER);
        assertThat(warning.priority()).isEqualTo(1);
        assertThat(digest.inAppMessages()).hasSize(1);
        assertThat(highlightedIdsOf(digest.inAppMessages().get(0))).containsExactly(1L, 2L);
    }

    @Test
    @Tag("D-410")
    void aDayWithOnlyTheSuspensionWarningSendsNoRegularDigestEmail() {
        ReminderInvoiceFact march = installment(1L, "2027-03-11");
        Map<Long, ResolvedReminderSchedule> schedules = Map.of(1L, schedule(bothChannels("warning", ReminderMessageType.BEFORE_SUSPENSION_REMINDER, ReminderMilestone.DUE_DATE, 4)));

        DailyReminderDigest digest = plan("2027-03-15", schedules, List.of(march));

        assertThat(digest.emailMessages()).hasSize(1);
        assertThat(digest.emailMessages().get(0).leadMessageType()).isEqualTo(ReminderMessageType.BEFORE_SUSPENSION_REMINDER);
    }

    @Test
    @Tag("D-346")
    void receiptUnderReviewIsListedWithoutNoticesAndItsNoticesAreSuppressedWithTheirReason() {
        ReminderInvoiceFact march = withReceiptUnderReview(installment(1L, "2027-03-11"));
        ReminderInvoiceFact fee = enrollmentFee(2L, "2027-03-11");

        DailyReminderDigest digest = plan("2027-03-12", sameScheduleFor(afterDueTheNextDay(), 1L, 2L), List.of(march, fee));

        ReminderMessage email = digest.emailMessages().get(0);
        assertThat(invoiceIdsOf(email)).containsExactly(1L, 2L);
        assertThat(highlightedIdsOf(email)).containsExactly(2L);
        assertThat(email.lines().get(0).receiptUnderReview()).isTrue();
        assertThat(email.lines().get(1).receiptUnderReview()).isFalse();
        assertThat(digest.suppressed()).containsExactlyInAnyOrder(
                new SuppressedReminder(1L, "after-due", ReminderChannel.IN_APP, "RECEIPT_UNDER_REVIEW"),
                new SuppressedReminder(1L, "after-due", ReminderChannel.EMAIL, "RECEIPT_UNDER_REVIEW"));
    }

    @Test
    @Tag("D-346")
    @Tag("D-186")
    void aPaymentUnderReviewIsListedWithoutNotices() {
        ReminderInvoiceFact march = withPaymentStatus(installment(1L, "2027-03-11"), InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);
        ReminderInvoiceFact fee = enrollmentFee(2L, "2027-03-11");

        DailyReminderDigest digest = plan("2027-03-12", sameScheduleFor(afterDueTheNextDay(), 1L, 2L), List.of(march, fee));

        ReminderMessage email = digest.emailMessages().get(0);
        assertThat(invoiceIdsOf(email)).containsExactly(1L, 2L);
        assertThat(highlightedIdsOf(email)).containsExactly(2L);
        assertThat(email.lines().get(0).receiptUnderReview()).isTrue();
        assertThat(digest.suppressed()).hasSize(2).allMatch(suppressed -> suppressed.reasonCode().equals("RECEIPT_UNDER_REVIEW"));
    }

    @Test
    @Tag("D-247")
    @Tag("D-354")
    void anInvoiceWithAnOpenCancellationRequestIsNeitherListedNorReminded() {
        ReminderInvoiceFact march = installment(1L, "2027-03-11");
        ReminderInvoiceFact may = withOpenCancellation(installment(2L, "2027-03-11"));

        DailyReminderDigest digest = plan("2027-03-12", sameScheduleFor(afterDueTheNextDay(), 1L, 2L), List.of(march, may));

        assertThat(invoiceIdsOf(digest.emailMessages().get(0))).containsExactly(1L);
        assertThat(digest.suppressed()).hasSize(2).allMatch(suppressed -> suppressed.invoiceId().equals(2L) && suppressed.reasonCode().equals("PENDING_CANCELLATION_REQUEST"));
    }

    @Test
    @Tag("D-354")
    @Tag("D-149")
    void onlyRealDebtIsListedAndALateFeeDebitNoteIsListedButNeverReminded() {
        ReminderInvoiceFact march = installment(1L, "2027-03-11");
        ReminderInvoiceFact marchLateFee = lateFeeNote(2L, 1L, "2027-03-11");
        ReminderInvoiceFact draft = ReminderDigestScenarios.invoice(3L, null, InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.DRAFT,
                InvoicePaymentStatus.PENDING, "2027-04-11", "80000.00", Currency.ARS, false, false);
        ReminderInvoiceFact exam = ReminderDigestScenarios.invoice(4L, null, InvoiceDocumentType.INVOICE, InvoiceType.EXAM_FEE, InvoiceDocumentStatus.ISSUED,
                InvoicePaymentStatus.PENDING, "2027-03-11", "80000.00", Currency.ARS, false, false);
        ReminderInvoiceFact paid = withPaymentStatus(installment(5L, "2027-02-11"), InvoicePaymentStatus.PAID);
        ReminderInvoiceFact settledLateFee = ReminderDigestScenarios.invoice(6L, 5L, InvoiceDocumentType.DEBIT_NOTE, InvoiceType.LATE_FEE, InvoiceDocumentStatus.ISSUED,
                InvoicePaymentStatus.PAID, "2027-02-11", "4000.00", Currency.ARS, false, false);

        DailyReminderDigest digest = plan("2027-03-12", sameScheduleFor(afterDueTheNextDay(), 1L, 3L, 4L, 5L), List.of(march, marchLateFee, draft, exam, paid, settledLateFee));

        ReminderMessage email = digest.emailMessages().get(0);
        assertThat(invoiceIdsOf(email)).containsExactly(1L, 2L);
        assertThat(highlightedIdsOf(email)).containsExactly(1L);
        assertThat(email.lines().get(1).debitNote()).isTrue();
    }

    @Test
    @Tag("D-412")
    void aStudentWithPreDueEmailsOffGetsTheEmailWithoutThePreDueLines() {
        ReminderInvoiceFact overdue = installment(1L, "2027-03-11");
        ReminderInvoiceFact upcoming = installment(2L, "2027-03-17");
        Map<Long, ResolvedReminderSchedule> schedules = Map.of(
                1L, schedule(bothChannels("after", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 4)),
                2L, schedule(bothChannels("before", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -2)));

        ReminderMessage email = plan("2027-03-15", schedules, List.of(overdue, upcoming)).emailMessages().get(0);

        assertThat(email.emailPrecedesDueDate()).isFalse();
        ReminderMessage withoutPreDue = email.withoutLinesPrecedingTheirDueDate().orElseThrow();
        assertThat(invoiceIdsOf(withoutPreDue)).containsExactly(1L);
        assertThat(highlightedIdsOf(withoutPreDue)).containsExactly(1L);
        assertThat(withoutPreDue.format()).isEqualTo(ReminderMessageFormat.OWN_NOTICE);
        assertThat(withoutPreDue.leadMessageType()).isEqualTo(ReminderMessageType.AFTER_DUE_REMINDER);
    }

    @Test
    @Tag("D-412")
    @Tag("D-372")
    void aDigestWithOnlyPreDueNoticesIsSuppressedForAStudentWithPreDueEmailsOff() {
        ReminderInvoiceFact overdue = installment(1L, "2027-03-11");
        ReminderInvoiceFact upcoming = installment(2L, "2027-03-17");
        Map<Long, ResolvedReminderSchedule> schedules = Map.of(
                1L, schedule(),
                2L, schedule(bothChannels("before", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -2)));

        ReminderMessage email = plan("2027-03-15", schedules, List.of(overdue, upcoming)).emailMessages().get(0);

        assertThat(email.emailPrecedesDueDate()).isTrue();
        assertThat(email.withoutLinesPrecedingTheirDueDate()).isEmpty();
    }

    @Test
    @Tag("D-372")
    void aBeforeDueTypeNoticeLandingAfterTheDueDateIsNotPreDue() {
        ReminderInvoiceFact march = installment(1L, "2027-03-11");
        Map<Long, ResolvedReminderSchedule> schedules = Map.of(1L, schedule(bothChannels("late", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, 2)));

        ReminderMessage email = plan("2027-03-15", schedules, List.of(march)).emailMessages().get(0);

        assertThat(email.emailPrecedesDueDate()).isFalse();
        assertThat(email.withoutLinesPrecedingTheirDueDate()).isPresent();
    }

    @Test
    @Tag("D-372")
    void aNoticeSentOnTheDueDateItselfIsNotPreDue() {
        ReminderInvoiceFact march = installment(1L, "2027-03-11");
        Map<Long, ResolvedReminderSchedule> schedules = Map.of(1L, schedule(bothChannels("same-day", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, 0)));

        DailyReminderDigest digest = plan("2027-03-11", schedules, List.of(march));

        assertThat(digest.emailMessages().get(0).emailPrecedesDueDate()).isFalse();
        assertThat(digest.emailMessages().get(0).lines().get(0).precedesDueDate()).isFalse();
        assertThat(digest.emailMessages().get(0).withoutLinesPrecedingTheirDueDate()).isPresent();
    }

    @Test
    @Tag("D-411")
    void withTheDigestOffEachNoticeGoesOutAsItsOwnMessage() {
        ReminderInvoiceFact march = installment(1L, "2027-03-11");
        ReminderInvoiceFact fee = enrollmentFee(2L, "2027-03-11");

        DailyReminderDigest digest = plan("2027-03-12", sameScheduleFor(afterDueTheNextDay(), 1L, 2L), List.of(march, fee), NonBusinessDays.weekendsOnly(),
                ReminderDigestMode.SEPARATE_MESSAGES, Map.of());

        assertThat(digest.inAppMessages()).hasSize(2);
        assertThat(digest.emailMessages()).hasSize(2);
        assertThat(digest.emailMessages()).allSatisfy(message -> {
            assertThat(message.format()).isEqualTo(ReminderMessageFormat.OWN_NOTICE);
            assertThat(message.lines()).hasSize(1);
        });
        assertThat(invoiceIdsOf(digest.emailMessages().get(0))).containsExactly(1L);
        assertThat(invoiceIdsOf(digest.emailMessages().get(1))).containsExactly(2L);
    }

    @Test
    @Tag("D-353")
    void eachLineKeepsTheConfigurationVersionsOfItsOwnInvoice() {
        ReminderInvoiceFact march = installment(1L, "2027-03-11");
        ReminderInvoiceFact marchLateFee = lateFeeNote(2L, 1L, "2027-03-11");

        ReminderMessage email = plan("2027-03-12", sameScheduleFor(afterDueTheNextDay(), 1L), List.of(march, marchLateFee)).emailMessages().get(0);

        assertThat(email.lines().get(0).versions().scheduleVersionId()).isEqualTo(10L);
        assertThat(email.lines().get(0).versions().exceptionVersionId()).isEqualTo(11L);
        assertThat(email.lines().get(0).versions().groupVersionId()).isEqualTo(12L);
        assertThat(email.lines().get(0).versions().globalSettingsVersionId()).isEqualTo(13L);
        assertThat(email.lines().get(1).versions().scheduleVersionId()).isNull();
    }

    @Test
    void aRemindableInvoiceWithoutResolvedScheduleIsRejected() {
        ReminderInvoiceFact march = installment(1L, "2027-03-11");

        assertThatThrownBy(() -> plan("2027-03-12", Map.of(), List.of(march))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1");
    }

    @Test
    @Tag("D-413")
    void anInvoiceIssuedAfterThePreviousRunGetsItsIssuanceNoticeTheNextDayAndNothingTheDayItWasIssued() {
        ReminderInvoiceFact lateIssued = new ReminderInvoiceFact(1L, null, com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType.INVOICE,
                com.red_escuela.backend.billing.invoice.domain.InvoiceType.MONTHLY_INSTALLMENT, com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus.ISSUED,
                com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus.PENDING, LocalDate.parse("2027-03-01"), LocalDate.parse("2027-03-11"), null,
                installment(1L, "2027-03-11").balance(), 1L, null, false, false, false, true);
        ResolvedReminderSchedule issuanceNotice = schedule(bothChannels("issuance", ReminderMessageType.ISSUANCE_NOTICE, ReminderMilestone.ISSUANCE, 0));
        Map<Long, ResolvedReminderSchedule> schedules = sameScheduleFor(issuanceNotice, 1L);

        DailyReminderDigest theIssueDay = plan("2027-03-01", schedules, List.of(lateIssued));
        DailyReminderDigest theNextDay = plan("2027-03-02", schedules, List.of(lateIssued));

        assertThat(theIssueDay.hasNothingToSend()).isTrue();
        assertThat(theNextDay.inAppMessages()).hasSize(1);
        assertThat(theNextDay.emailMessages()).hasSize(1);
    }

    private static final String WAITS_FOR_PRIOR_DEBT = "NEW_INSTALLMENT_WAITS_FOR_PRIOR_DEBT";

    private static ResolvedReminderSchedule defaultSchedule() {
        return schedule(ReminderSchedule.initial().notices().toArray(new ReminderNotice[0]));
    }

    private static ReminderInvoiceFact marchInstallment() {
        return ReminderInvoiceFacts.installment(1L, "2027-03-01", "2027-03-11");
    }

    private static ReminderInvoiceFact aprilInstallment() {
        return ReminderInvoiceFacts.installment(2L, "2027-04-01", "2027-04-11");
    }

    private static DailyReminderDigest planAprilWithMarchOpen(String date) {
        return plan(date, sameScheduleFor(defaultSchedule(), 1L, 2L), List.of(marchInstallment(), aprilInstallment()));
    }

    @Test
    @Tag("D-347")
    @Tag("D-344")
    @Tag("D-409")
    void issuanceWithAPriorDebtBecomesADebtSummaryByInAppAndEmailListingTheOlderInstallmentFirst() {
        DailyReminderDigest digest = planAprilWithMarchOpen("2027-04-01");

        assertThat(digest.inAppMessages()).hasSize(1);
        assertThat(digest.emailMessages()).hasSize(1);
        for (ReminderMessage message : List.of(digest.inAppMessages().get(0), digest.emailMessages().get(0))) {
            assertThat(message.format()).isEqualTo(ReminderMessageFormat.DEBT_SUMMARY);
            assertThat(invoiceIdsOf(message)).containsExactly(1L, 2L);
            assertThat(highlightedIdsOf(message)).containsExactly(2L);
            assertThat(message.leadMessageType()).isEqualTo(ReminderMessageType.ISSUANCE_NOTICE);
            assertThat(message.priority()).isEqualTo(3);
        }
    }

    @Test
    @Tag("D-347")
    @Tag("D-344")
    void theDebtSummaryEmailCannotBeSwitchedOffByThePreDueOptOut() {
        ReminderMessage email = planAprilWithMarchOpen("2027-04-01").emailMessages().get(0);

        assertThat(email.emailPrecedesDueDate()).isFalse();
        assertThat(email.withoutLinesPrecedingTheirDueDate()).containsSame(email);
    }

    @Test
    @Tag("D-347")
    @Tag("D-344")
    void issuanceWithoutPriorDebtStaysAnInAppOwnNoticeWithoutEmail() {
        DailyReminderDigest digest = plan("2027-04-01", sameScheduleFor(defaultSchedule(), 2L), List.of(aprilInstallment()));

        assertThat(digest.emailMessages()).isEmpty();
        assertThat(digest.inAppMessages()).hasSize(1);
        assertThat(digest.inAppMessages().get(0).format()).isEqualTo(ReminderMessageFormat.OWN_NOTICE);
    }

    @Test
    @Tag("D-347")
    void theNewInstallmentOwnNoticesAreSuppressedInBothChannelsWhileTheEarlierOneIsOpen() {
        DailyReminderDigest afterIssue = planAprilWithMarchOpen("2027-04-06");
        DailyReminderDigest beforeDueEmail = planAprilWithMarchOpen("2027-04-09");
        DailyReminderDigest beforeDueInApp = planAprilWithMarchOpen("2027-04-10");

        assertThat(afterIssue.hasNothingToSend()).isTrue();
        assertThat(afterIssue.suppressed()).containsExactlyInAnyOrder(
                new SuppressedReminder(2L, "after-issue", ReminderChannel.IN_APP, WAITS_FOR_PRIOR_DEBT),
                new SuppressedReminder(2L, "after-issue", ReminderChannel.EMAIL, WAITS_FOR_PRIOR_DEBT));
        assertThat(beforeDueEmail.hasNothingToSend()).isTrue();
        assertThat(beforeDueEmail.suppressed()).containsExactly(new SuppressedReminder(2L, "before-due", ReminderChannel.EMAIL, WAITS_FOR_PRIOR_DEBT));
        assertThat(beforeDueInApp.hasNothingToSend()).isTrue();
        assertThat(beforeDueInApp.suppressed()).containsExactly(new SuppressedReminder(2L, "before-due", ReminderChannel.IN_APP, WAITS_FOR_PRIOR_DEBT));
    }

    @Test
    @Tag("D-347")
    void withTheEarlierInstallmentPaidTheNewOneKeepsItsOwnCadenceOnTheSixthAndTheNinth() {
        Map<Long, ResolvedReminderSchedule> schedules = sameScheduleFor(defaultSchedule(), 2L);
        List<ReminderInvoiceFact> onlyApril = List.of(aprilInstallment());

        DailyReminderDigest sixth = plan("2027-04-06", schedules, onlyApril);
        DailyReminderDigest ninth = plan("2027-04-09", schedules, onlyApril);
        DailyReminderDigest tenth = plan("2027-04-10", schedules, onlyApril);

        assertThat(sixth.emailMessages()).hasSize(1);
        assertThat(sixth.emailMessages().get(0).leadMessageType()).isEqualTo(ReminderMessageType.AFTER_ISSUE_REMINDER);
        assertThat(ninth.emailMessages()).hasSize(1);
        assertThat(ninth.emailMessages().get(0).leadMessageType()).isEqualTo(ReminderMessageType.BEFORE_DUE_REMINDER);
        assertThat(tenth.emailMessages()).isEmpty();
        assertThat(tenth.inAppMessages()).hasSize(1);
        assertThat(sixth.suppressed()).isEmpty();
    }

    @Test
    @Tag("D-347")
    @Tag("D-346")
    void anEarlierInstallmentWithAReceiptUnderReviewStillCountsAsUnpaid() {
        ReminderInvoiceFact marchUnderReview = withReceiptUnderReview(installment(1L, "2027-03-11"));
        ReminderInvoiceFact april = aprilInstallment();
        Map<Long, ResolvedReminderSchedule> schedules = sameScheduleFor(defaultSchedule(), 1L, 2L);

        DailyReminderDigest issueDay = plan("2027-04-01", schedules, List.of(marchUnderReview, april));
        DailyReminderDigest afterIssue = plan("2027-04-06", schedules, List.of(marchUnderReview, april));

        assertThat(issueDay.emailMessages().get(0).format()).isEqualTo(ReminderMessageFormat.DEBT_SUMMARY);
        assertThat(issueDay.emailMessages().get(0).lines().get(0).receiptUnderReview()).isTrue();
        assertThat(afterIssue.hasNothingToSend()).isTrue();
    }

    @Test
    @Tag("D-347")
    @Tag("D-344")
    void anUnpaidLateFeeNoteOfAnEarlierInvoiceCountsAsPriorDebtForTheSummary() {
        ReminderInvoiceFact note = ReminderInvoiceFacts.lateFeeDebitNote(9L, 1L, "2027-03-20", "2027-03-30");

        DailyReminderDigest digest = plan("2027-04-01", sameScheduleFor(defaultSchedule(), 2L), List.of(note, aprilInstallment()));

        assertThat(digest.emailMessages()).hasSize(1);
        assertThat(digest.emailMessages().get(0).format()).isEqualTo(ReminderMessageFormat.DEBT_SUMMARY);
        assertThat(invoiceIdsOf(digest.emailMessages().get(0))).containsExactly(9L, 2L);
    }

    @Test
    @Tag("D-347")
    void withoutAnIssuanceNoticeInTheScheduleThereIsNoSummary() {
        ResolvedReminderSchedule withoutIssuance = schedule(bothChannels("after-issue", ReminderMessageType.AFTER_ISSUE_REMINDER, ReminderMilestone.ISSUANCE, 5));

        DailyReminderDigest digest = plan("2027-04-01", sameScheduleFor(withoutIssuance, 1L, 2L), List.of(marchInstallment(), aprilInstallment()));

        assertThat(digest.hasNothingToSend()).isTrue();
    }

    @Test
    @Tag("D-347")
    @Tag("D-342")
    void aSummaryIssuedOnASaturdayGoesInAppThatDayAndByEmailOnTheMonday() {
        ReminderInvoiceFact may = ReminderInvoiceFacts.installment(3L, "2027-05-01", "2027-05-11");
        Map<Long, ResolvedReminderSchedule> schedules = sameScheduleFor(defaultSchedule(), 1L, 3L);
        List<ReminderInvoiceFact> facts = List.of(marchInstallment(), may);

        DailyReminderDigest saturday = plan("2027-05-01", schedules, facts);
        DailyReminderDigest monday = plan("2027-05-03", schedules, facts);

        assertThat(saturday.inAppMessages().get(0).format()).isEqualTo(ReminderMessageFormat.DEBT_SUMMARY);
        assertThat(saturday.emailMessages()).isEmpty();
        assertThat(monday.inAppMessages()).isEmpty();
        assertThat(monday.emailMessages()).hasSize(1);
        assertThat(monday.emailMessages().get(0).format()).isEqualTo(ReminderMessageFormat.DEBT_SUMMARY);
    }

    @Test
    @Tag("D-347")
    @Tag("D-410")
    void theSummaryAbsorbsTheOtherNoticesOfTheSameDayIntoOneMessage() {
        ResolvedReminderSchedule marchSchedule = schedule(bothChannels("after-due", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 21));
        Map<Long, ResolvedReminderSchedule> schedules = Map.of(1L, marchSchedule, 2L, defaultSchedule());

        DailyReminderDigest digest = plan("2027-04-01", schedules, List.of(marchInstallment(), aprilInstallment()));

        assertThat(digest.emailMessages()).hasSize(1);
        ReminderMessage email = digest.emailMessages().get(0);
        assertThat(email.format()).isEqualTo(ReminderMessageFormat.DEBT_SUMMARY);
        assertThat(highlightedIdsOf(email)).containsExactly(1L, 2L);
        assertThat(email.leadMessageType()).isEqualTo(ReminderMessageType.AFTER_DUE_REMINDER);
    }

    @Test
    @Tag("D-347")
    @Tag("D-403")
    void inTheSeparateMessagesModeTheSummaryStillListsAllTheDebt() {
        DailyReminderDigest digest = plan("2027-04-01", sameScheduleFor(defaultSchedule(), 1L, 2L), List.of(marchInstallment(), aprilInstallment()),
                NonBusinessDays.weekendsOnly(), ReminderDigestMode.SEPARATE_MESSAGES, Map.of());

        assertThat(digest.emailMessages()).hasSize(1);
        assertThat(digest.emailMessages().get(0).format()).isEqualTo(ReminderMessageFormat.DEBT_SUMMARY);
        assertThat(invoiceIdsOf(digest.emailMessages().get(0))).containsExactly(1L, 2L);
    }

    @Test
    @Tag("D-347")
    @Tag("D-391")
    void thePlannerHasNoMonthlyCapSoTheSummaryComesOutWhateverWasSentBefore() {
        ReminderInvoiceFact may = ReminderInvoiceFacts.installment(3L, "2027-05-04", "2027-05-14");
        Map<Long, ResolvedReminderSchedule> schedules = sameScheduleFor(defaultSchedule(), 2L, 3L);

        DailyReminderDigest digest = plan("2027-05-04", schedules, List.of(aprilInstallment(), may));

        assertThat(digest.emailMessages()).hasSize(1);
        assertThat(digest.emailMessages().get(0).format()).isEqualTo(ReminderMessageFormat.DEBT_SUMMARY);
    }
}
