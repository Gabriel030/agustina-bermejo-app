package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.NonBusinessDays;
import com.red_escuela.backend.billing.collections.domain.ReminderDigestMode;
import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFact;
import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFacts;
import com.red_escuela.backend.billing.collections.domain.ReminderStudentFacts;
import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleSource;
import com.red_escuela.backend.billing.configuration.domain.ResolvedReminderSchedule;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionReminderImpactCalculatorTest {
    private static final LocalDate WINDOW_START = LocalDate.of(2027, 3, 4);
    private static final LocalDate WINDOW_END = LocalDate.of(2027, 4, 3);
    private static final NonBusinessDays WEEKENDS = NonBusinessDays.weekendsOnly();

    private static ReminderStudentFacts student(long studentId, ReminderInvoiceFact... invoices) {
        return new ReminderStudentFacts(studentId, List.of(invoices), Map.of());
    }

    private static ReminderInvoiceFact installmentDueOnMarch11(long invoiceId) {
        return ReminderInvoiceFacts.installment(invoiceId, "2027-02-26", "2027-03-11");
    }

    private static ResolvedReminderSchedule schedule(ReminderNotice... notices) {
        return new ResolvedReminderSchedule(ReminderScheduleSource.TEMPLATE, null, null, null, null, List.of(notices), CollectionReminderSettings.initial());
    }

    private static ReminderNotice beforeDue(int offsetDays, EnumSet<ReminderChannel> channels) {
        return ReminderNotice.of("before-due", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, offsetDays, channels);
    }

    private static ResolvedReminderSchedule beforeDueSchedule(int offsetDays) {
        return schedule(beforeDue(offsetDays, EnumSet.allOf(ReminderChannel.class)));
    }

    private static int count(List<ReminderStudentFacts> students, Map<Long, ResolvedReminderSchedule> current, Map<Long, ResolvedReminderSchedule> proposed) {
        return CollectionReminderImpactCalculator.countStudentsWithChangedSchedule(students, current, proposed, ReminderDigestMode.DAILY_DIGEST, ReminderDigestMode.DAILY_DIGEST,
                WINDOW_START, WINDOW_END, WEEKENDS);
    }

    @Test
    @Tag("D-350")
    void aProposalEqualToTheCurrentScheduleChangesNobody() {
        List<ReminderStudentFacts> students = List.of(student(1, installmentDueOnMarch11(10)), student(2, installmentDueOnMarch11(20)));
        Map<Long, ResolvedReminderSchedule> schedules = Map.of(10L, beforeDueSchedule(-1), 20L, beforeDueSchedule(-1));

        assertThat(count(students, schedules, schedules)).isZero();
    }

    @Test
    @Tag("D-350")
    void onlyTheStudentsWhoseOwnNoticeMovesAreCounted() {
        List<ReminderStudentFacts> students = List.of(student(1, installmentDueOnMarch11(10)), student(2, installmentDueOnMarch11(20)), student(3, installmentDueOnMarch11(30)));
        Map<Long, ResolvedReminderSchedule> current = Map.of(10L, beforeDueSchedule(-1), 20L, beforeDueSchedule(-1), 30L, beforeDueSchedule(-1));
        Map<Long, ResolvedReminderSchedule> proposed = Map.of(10L, beforeDueSchedule(-3), 20L, beforeDueSchedule(-1), 30L, beforeDueSchedule(-3));

        assertThat(count(students, current, proposed)).isEqualTo(2);
    }

    @Test
    @Tag("D-350")
    void aStudentWithSeveralInvoicesIsCountedOnceEvenIfEveryInvoiceChanges() {
        List<ReminderStudentFacts> students = List.of(student(1, installmentDueOnMarch11(10), installmentDueOnMarch11(11)));

        assertThat(count(students, Map.of(10L, beforeDueSchedule(-1), 11L, beforeDueSchedule(-1)), Map.of(10L, beforeDueSchedule(-2), 11L, beforeDueSchedule(-2)))).isEqualTo(1);
    }

    @Test
    @Tag("D-350")
    void aStudentIsCountedWhenOnlyOneOfHisInvoicesChanges() {
        List<ReminderStudentFacts> students = List.of(student(1, installmentDueOnMarch11(10), installmentDueOnMarch11(11)));

        assertThat(count(students, Map.of(10L, beforeDueSchedule(-1), 11L, beforeDueSchedule(-1)), Map.of(10L, beforeDueSchedule(-1), 11L, beforeDueSchedule(-2)))).isEqualTo(1);
    }

    @ParameterizedTest(name = "due {0}, current offset {1}, proposed offset {2} -> {3}")
    @Tag("D-350")
    @CsvSource({
            "2027-03-12, -1, -2, 1",
            "2027-03-05, -1, -2, 1",
            "2027-03-04, -1, -2, 0",
            "2027-03-05, -1, 0, 1",
            "2027-04-03, 0, -1, 1",
            "2027-04-05, -1, 0, 0",
            "2027-04-05, -1, -2, 1",
            "2027-04-06, -1, -2, 0",
            "2027-04-06, -3, -2, 1",
            "2027-04-07, -3, -2, 0"
    })
    void onlyNoticesFiringInsideTheWindowCount(String dueDate, int currentOffset, int proposedOffset, int expected) {
        ReminderInvoiceFact invoice = ReminderInvoiceFacts.installment(10, "2027-02-01", dueDate);
        List<ReminderStudentFacts> students = List.of(student(1, invoice));

        assertThat(count(students, Map.of(10L, beforeDueSchedule(currentOffset)), Map.of(10L, beforeDueSchedule(proposedOffset)))).isEqualTo(expected);
    }

    @Test
    @Tag("D-350")
    void aNoticeMovingOutsideTheWindowOnlyIsStillAChangeInsideIt() {
        ReminderInvoiceFact invoice = ReminderInvoiceFacts.installment(10, "2027-02-01", "2027-03-05");
        List<ReminderStudentFacts> students = List.of(student(1, invoice));

        assertThat(count(students, Map.of(10L, beforeDueSchedule(-1)), Map.of(10L, beforeDueSchedule(-3)))).isEqualTo(1);
    }

    @Test
    @Tag("D-350")
    void aChannelChangeOnTheSameDayCounts() {
        List<ReminderStudentFacts> students = List.of(student(1, installmentDueOnMarch11(10)));

        assertThat(count(students, Map.of(10L, beforeDueSchedule(-1)), Map.of(10L, schedule(beforeDue(-1, EnumSet.of(ReminderChannel.IN_APP)))))).isEqualTo(1);
    }

    @Test
    @Tag("D-350")
    void aRenamedNoticeOnTheSameDayCounts() {
        List<ReminderStudentFacts> students = List.of(student(1, installmentDueOnMarch11(10)));
        ResolvedReminderSchedule renamed = schedule(ReminderNotice.of("heads-up", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -1,
                EnumSet.allOf(ReminderChannel.class)));

        assertThat(count(students, Map.of(10L, beforeDueSchedule(-1)), Map.of(10L, renamed))).isEqualTo(1);
    }

    @Test
    @Tag("D-350")
    void anEmailMovedToTheNextBusinessDayByTheCalendarIsComparedByItsRealSendDate() {
        ReminderInvoiceFact invoice = ReminderInvoiceFacts.installment(10, "2027-02-01", "2027-03-12");
        List<ReminderStudentFacts> students = List.of(student(1, invoice));
        ResolvedReminderSchedule emailOnly = schedule(beforeDue(-1, EnumSet.of(ReminderChannel.EMAIL)));
        ResolvedReminderSchedule emailOnlyTwoDaysBefore = schedule(beforeDue(-2, EnumSet.of(ReminderChannel.EMAIL)));

        assertThat(count(students, Map.of(10L, emailOnly), Map.of(10L, emailOnlyTwoDaysBefore))).isEqualTo(1);
    }

    @Test
    @Tag("D-350")
    void invoicesThatNeverSendRemindersDoNotCount() {
        ReminderInvoiceFact underReview = ReminderInvoiceFacts.fact(10, null, InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.ISSUED,
                InvoicePaymentStatus.PENDING, "2027-02-26", "2027-03-11", null, true, false);
        ReminderInvoiceFact pendingCancellation = ReminderInvoiceFacts.fact(11, null, InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.ISSUED,
                InvoicePaymentStatus.PENDING, "2027-02-26", "2027-03-11", null, false, true);
        ReminderInvoiceFact paid = ReminderInvoiceFacts.fact(12, null, InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.ISSUED,
                InvoicePaymentStatus.PAID, "2027-02-26", "2027-03-11", null, false, false);
        List<ReminderStudentFacts> students = List.of(student(1, underReview), student(2, pendingCancellation), student(3, paid));

        assertThat(count(students, Map.of(10L, beforeDueSchedule(-1), 11L, beforeDueSchedule(-1), 12L, beforeDueSchedule(-1)),
                Map.of(10L, beforeDueSchedule(-3), 11L, beforeDueSchedule(-3), 12L, beforeDueSchedule(-3)))).isZero();
    }

    @Test
    @Tag("D-350")
    void anInvoiceWithoutAResolvedScheduleIsIgnored() {
        List<ReminderStudentFacts> students = List.of(student(1, installmentDueOnMarch11(10)));

        assertThat(count(students, Map.of(), Map.of(10L, beforeDueSchedule(-3)))).isEqualTo(1);
        assertThat(count(students, Map.of(10L, beforeDueSchedule(-3)), Map.of())).isEqualTo(1);
        assertThat(count(students, Map.of(), Map.of())).isZero();
    }

    @Test
    @Tag("D-350")
    void aDigestModeChangeCountsOnlyStudentsWithSomethingFiringInTheWindow() {
        List<ReminderStudentFacts> students = List.of(student(1, installmentDueOnMarch11(10)), student(2, ReminderInvoiceFacts.installment(20, "2027-02-01", "2027-09-30")));
        Map<Long, ResolvedReminderSchedule> schedules = Map.of(10L, beforeDueSchedule(-1), 20L, beforeDueSchedule(-1));

        int changed = CollectionReminderImpactCalculator.countStudentsWithChangedSchedule(students, schedules, schedules, ReminderDigestMode.DAILY_DIGEST,
                ReminderDigestMode.SEPARATE_MESSAGES, WINDOW_START, WINDOW_END, WEEKENDS);

        assertThat(changed).isEqualTo(1);
    }

    @Test
    @Tag("D-350")
    void theSameDigestModeNeverChangesAnything() {
        List<ReminderStudentFacts> students = List.of(student(1, installmentDueOnMarch11(10)));
        Map<Long, ResolvedReminderSchedule> schedules = Map.of(10L, beforeDueSchedule(-1));

        int changed = CollectionReminderImpactCalculator.countStudentsWithChangedSchedule(students, schedules, schedules, ReminderDigestMode.SEPARATE_MESSAGES,
                ReminderDigestMode.SEPARATE_MESSAGES, WINDOW_START, WINDOW_END, WEEKENDS);

        assertThat(changed).isZero();
    }

    @Test
    @Tag("D-350")
    void aWindowThatEndsBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> CollectionReminderImpactCalculator.countStudentsWithChangedSchedule(List.of(), Map.of(), Map.of(), ReminderDigestMode.DAILY_DIGEST,
                ReminderDigestMode.DAILY_DIGEST, WINDOW_END, WINDOW_START, WEEKENDS)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Tag("D-350")
    void noStudentsMeansNoImpact() {
        assertThat(count(List.of(), Map.of(), Map.of())).isZero();
    }
}
