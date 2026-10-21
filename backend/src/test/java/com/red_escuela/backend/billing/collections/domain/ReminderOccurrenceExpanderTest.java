package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import com.red_escuela.backend.billing.configuration.domain.ReminderRepetition;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ReminderOccurrenceExpanderTest {

    private static ReminderNotice notice(String key, ReminderMilestone milestone, int offsetDays, ReminderRepetition repetition) {
        return new ReminderNotice(key, ReminderMessageType.AFTER_ISSUE_REMINDER, milestone, offsetDays, EnumSet.of(ReminderChannel.EMAIL), repetition);
    }

    private static List<LocalDate> datesOf(List<ReminderOccurrence> occurrences) {
        return occurrences.stream().map(ReminderOccurrence::date).toList();
    }

    @Test
    @Tag("D-368")
    void expandsEachRepetitionUntilTheMilestoneExclusive() {
        ReminderInvoiceFact march = ReminderInvoiceFacts.fact(1L, null, InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.ISSUED,
                InvoicePaymentStatus.PENDING, "2027-03-01", "2027-03-11", "2027-03-31", false, false);
        ReminderNotice everyThreeDaysUntilDue = notice("a", ReminderMilestone.ISSUANCE, 1, new ReminderRepetition(3, ReminderMilestone.DUE_DATE));

        List<ReminderOccurrence> occurrences = ReminderOccurrenceExpander.expandOccurrencesOf(march, List.of(everyThreeDaysUntilDue));

        assertThat(datesOf(occurrences)).containsExactly(LocalDate.parse("2027-03-02"), LocalDate.parse("2027-03-05"), LocalDate.parse("2027-03-08"));
    }

    @Test
    @Tag("D-368")
    void aRepetitionLandingExactlyOnTheUntilMilestoneIsNotEmitted() {
        ReminderInvoiceFact march = ReminderInvoiceFacts.installment(1L, "2027-03-01", "2027-03-11");
        ReminderNotice landsOnDue = notice("a", ReminderMilestone.ISSUANCE, 0, new ReminderRepetition(5, ReminderMilestone.DUE_DATE));

        List<ReminderOccurrence> occurrences = ReminderOccurrenceExpander.expandOccurrencesOf(march, List.of(landsOnDue));

        assertThat(datesOf(occurrences)).containsExactly(LocalDate.parse("2027-03-01"), LocalDate.parse("2027-03-06"));
    }

    @Test
    @Tag("D-372")
    @Tag("D-368")
    void repetitionCrossingTheDueDateIsJudgedPerOccurrence() {
        ReminderInvoiceFact march = ReminderInvoiceFacts.fact(1L, null, InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.ISSUED,
                InvoicePaymentStatus.PENDING, "2027-03-01", "2027-03-11", "2027-03-31", false, false);
        ReminderNotice everyFourDaysUntilSuspension = notice("a", ReminderMilestone.ISSUANCE, 5, new ReminderRepetition(4, ReminderMilestone.SUSPENSION));

        List<ReminderOccurrence> occurrences = ReminderOccurrenceExpander.expandOccurrencesOf(march, List.of(everyFourDaysUntilSuspension));

        assertThat(occurrences).extracting(ReminderOccurrence::date, ReminderOccurrence::fallsBeforeDueDate).containsExactly(
                tuple(LocalDate.parse("2027-03-06"), true),
                tuple(LocalDate.parse("2027-03-10"), true),
                tuple(LocalDate.parse("2027-03-14"), false),
                tuple(LocalDate.parse("2027-03-18"), false),
                tuple(LocalDate.parse("2027-03-22"), false),
                tuple(LocalDate.parse("2027-03-26"), false),
                tuple(LocalDate.parse("2027-03-30"), false));
    }

    @Test
    @Tag("D-368")
    void suspensionMilestoneWithoutDateYieldsNoOccurrence() {
        ReminderInvoiceFact withoutSuspensionDate = ReminderInvoiceFacts.installment(1L, "2027-03-01", "2027-03-11");
        ReminderNotice beforeSuspension = notice("a", ReminderMilestone.SUSPENSION, -1, null);
        ReminderNotice repeatingFromSuspension = notice("b", ReminderMilestone.SUSPENSION, -5, new ReminderRepetition(2, ReminderMilestone.SUSPENSION));

        assertThat(ReminderOccurrenceExpander.expandOccurrencesOf(withoutSuspensionDate, List.of(beforeSuspension, repeatingFromSuspension))).isEmpty();
    }

    @Test
    @Tag("D-368")
    void repetitionUntilAnUnknownSuspensionDateYieldsOnlyTheFirstOccurrence() {
        ReminderInvoiceFact withoutSuspensionDate = ReminderInvoiceFacts.installment(1L, "2027-03-01", "2027-03-11");
        ReminderNotice repeatingUntilSuspension = notice("a", ReminderMilestone.DUE_DATE, 1, new ReminderRepetition(3, ReminderMilestone.SUSPENSION));

        assertThat(datesOf(ReminderOccurrenceExpander.expandOccurrencesOf(withoutSuspensionDate, List.of(repeatingUntilSuspension)))).containsExactly(LocalDate.parse("2027-03-12"));
    }

    @Test
    @Tag("D-368")
    void occurrenceBeforeIssueDateIsDropped() {
        ReminderInvoiceFact issuedLate = ReminderInvoiceFacts.installment(1L, "2027-03-05", "2027-03-15");
        ReminderNotice dueMinusTenDaysRepeatingEveryFive = notice("a", ReminderMilestone.DUE_DATE, -12, new ReminderRepetition(5, ReminderMilestone.DUE_DATE));

        List<ReminderOccurrence> occurrences = ReminderOccurrenceExpander.expandOccurrencesOf(issuedLate, List.of(dueMinusTenDaysRepeatingEveryFive));

        assertThat(datesOf(occurrences)).containsExactly(LocalDate.parse("2027-03-08"), LocalDate.parse("2027-03-13"));
    }

    @Test
    @Tag("D-368")
    void anOccurrenceOnTheIssueDateIsKept() {
        ReminderInvoiceFact issued = ReminderInvoiceFacts.installment(1L, "2027-03-05", "2027-03-15");
        ReminderNotice onIssue = notice("a", ReminderMilestone.ISSUANCE, 0, null);

        assertThat(datesOf(ReminderOccurrenceExpander.expandOccurrencesOf(issued, List.of(onIssue)))).containsExactly(LocalDate.parse("2027-03-05"));
    }

    @Test
    @Tag("D-368")
    void occurrencesFromSeveralNoticesComeSortedByDateThenKey() {
        ReminderInvoiceFact march = ReminderInvoiceFacts.installment(1L, "2027-03-01", "2027-03-11");
        ReminderNotice second = notice("b", ReminderMilestone.ISSUANCE, 5, null);
        ReminderNotice first = notice("a", ReminderMilestone.ISSUANCE, 5, null);
        ReminderNotice earlier = notice("z", ReminderMilestone.ISSUANCE, 1, null);

        List<ReminderOccurrence> occurrences = ReminderOccurrenceExpander.expandOccurrencesOf(march, List.of(second, first, earlier));

        assertThat(occurrences).extracting(ReminderOccurrence::noticeKey).containsExactly("z", "a", "b");
    }

    @Test
    @Tag("D-368")
    void aRepetitionEveryZeroDaysDoesNotLoopForever() {
        ReminderInvoiceFact march = ReminderInvoiceFacts.installment(1L, "2027-03-01", "2027-03-11");
        ReminderNotice degenerate = notice("a", ReminderMilestone.ISSUANCE, 1, new ReminderRepetition(0, ReminderMilestone.DUE_DATE));

        assertThat(datesOf(ReminderOccurrenceExpander.expandOccurrencesOf(march, List.of(degenerate)))).containsExactly(LocalDate.parse("2027-03-02"));
    }

    @Test
    @Tag("D-354")
    void afterDueNoticeIgnoresGrace() {
        ReminderInvoiceFact insideGrace = ReminderInvoiceFacts.installment(1L, "2027-03-01", "2027-03-11");
        ReminderNotice tenDaysAfterDue = new ReminderNotice("a", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 10,
                EnumSet.of(ReminderChannel.IN_APP, ReminderChannel.EMAIL), null);

        List<ReminderOccurrence> occurrences = ReminderOccurrenceExpander.expandOccurrencesOf(insideGrace, List.of(tenDaysAfterDue));

        assertThat(datesOf(occurrences)).containsExactly(LocalDate.parse("2027-03-21"));
        assertThat(new com.red_escuela.backend.billing.collections.policy.ReminderEligibleInvoicePolicy().evaluateRule(insideGrace).allowed()).isTrue();
    }
}
