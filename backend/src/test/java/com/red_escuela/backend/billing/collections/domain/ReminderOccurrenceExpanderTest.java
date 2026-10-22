package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestoneDates;
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

        assertThat(occurrences).extracting(ReminderOccurrence::date, occurrence -> occurrence.emailPrecedesDueDate(NonBusinessDays.weekendsOnly())).containsExactly(
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
    @Tag("D-413")
    void repetitionUntilSuspensionKeepsRepeatingWhileTheEnrollmentIsNotSuspendedAndThereIsNoSuspensionDate() {
        ReminderInvoiceFact march = ReminderInvoiceFacts.installmentOfAnEnrollment(1L, "2027-03-01", "2027-03-11", false, false);
        ReminderNotice everyThreeDaysUntilSuspension = notice("a", ReminderMilestone.DUE_DATE, 1, new ReminderRepetition(3, ReminderMilestone.SUSPENSION));

        List<ReminderOccurrence> occurrences = ReminderOccurrenceExpander.expandOccurrencesUpTo(march, List.of(everyThreeDaysUntilSuspension), LocalDate.parse("2027-03-24"));

        assertThat(datesOf(occurrences)).containsExactly(LocalDate.parse("2027-03-12"), LocalDate.parse("2027-03-15"), LocalDate.parse("2027-03-18"),
                LocalDate.parse("2027-03-21"), LocalDate.parse("2027-03-24"));
    }

    @Test
    @Tag("D-413")
    void repetitionUntilSuspensionStopsRepeatingOnceTheEnrollmentIsSuspended() {
        ReminderInvoiceFact march = ReminderInvoiceFacts.installmentOfAnEnrollment(1L, "2027-03-01", "2027-03-11", true, false);
        ReminderNotice everyThreeDaysUntilSuspension = notice("a", ReminderMilestone.DUE_DATE, 1, new ReminderRepetition(3, ReminderMilestone.SUSPENSION));

        List<ReminderOccurrence> occurrences = ReminderOccurrenceExpander.expandOccurrencesUpTo(march, List.of(everyThreeDaysUntilSuspension), LocalDate.parse("2027-03-25"));

        assertThat(datesOf(occurrences)).containsExactly(LocalDate.parse("2027-03-12"));
    }

    @Test
    @Tag("D-413")
    void aKnownSuspensionDateStillBoundsTheRepetitionEvenWhenTheHorizonIsFurther() {
        ReminderInvoiceFact march = ReminderInvoiceFacts.fact(1L, null, InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.ISSUED,
                InvoicePaymentStatus.PENDING, "2027-03-01", "2027-03-11", "2027-03-20", false, false);
        ReminderNotice everyThreeDaysUntilSuspension = notice("a", ReminderMilestone.DUE_DATE, 1, new ReminderRepetition(3, ReminderMilestone.SUSPENSION));

        List<ReminderOccurrence> occurrences = ReminderOccurrenceExpander.expandOccurrencesUpTo(march, List.of(everyThreeDaysUntilSuspension), LocalDate.parse("2027-04-30"));

        assertThat(datesOf(occurrences)).containsExactly(LocalDate.parse("2027-03-12"), LocalDate.parse("2027-03-15"), LocalDate.parse("2027-03-18"));
    }

    @Test
    @Tag("D-413")
    void anInvoiceIssuedAfterThePreviousRunRecoversOnlyItsIssueDayNoticeOneDayLater() {
        ReminderInvoiceFact march = ReminderInvoiceFacts.installmentOfAnEnrollment(1L, "2027-03-01", "2027-03-11", false, true);
        ReminderNotice onIssueDay = notice("issue", ReminderMilestone.ISSUANCE, 0, null);
        ReminderNotice fiveDaysLater = notice("later", ReminderMilestone.ISSUANCE, 5, null);

        List<ReminderOccurrence> occurrences = ReminderOccurrenceExpander.expandOccurrencesUpTo(march, List.of(onIssueDay, fiveDaysLater), LocalDate.parse("2027-03-20"));

        assertThat(occurrences).extracting(ReminderOccurrence::noticeKey, ReminderOccurrence::date)
                .containsExactly(org.assertj.core.api.Assertions.tuple("issue", LocalDate.parse("2027-03-02")), org.assertj.core.api.Assertions.tuple("later", LocalDate.parse("2027-03-06")));
    }

    @Test
    @Tag("D-413")
    void anOpenEndedExpansionOnlyRepeatsNoticesThatRepeatUntilTheSuspension() {
        ReminderMilestoneDates withoutSuspension = ReminderMilestoneDates.withoutSuspension(LocalDate.parse("2027-03-01"), LocalDate.parse("2027-03-11"));
        ReminderNotice untilDue = notice("a", ReminderMilestone.DUE_DATE, 1, new ReminderRepetition(3, ReminderMilestone.DUE_DATE));
        ReminderNotice withoutRepetition = notice("b", ReminderMilestone.DUE_DATE, 1, null);
        ReminderNotice everyZeroDays = notice("c", ReminderMilestone.DUE_DATE, 1, new ReminderRepetition(0, ReminderMilestone.SUSPENSION));
        ReminderNotice anchoredOnTheSuspension = notice("d", ReminderMilestone.SUSPENSION, -1, new ReminderRepetition(3, ReminderMilestone.SUSPENSION));
        LocalDate horizon = LocalDate.parse("2027-04-30");

        assertThat(untilDue.repeatsUntilSuspension()).isFalse();
        assertThat(withoutRepetition.repeatsUntilSuspension()).isFalse();
        assertThat(everyZeroDays.repeatsUntilSuspension()).isFalse();
        assertThat(untilDue.openEndedOccurrenceDatesFor(withoutSuspension, horizon)).containsExactly(LocalDate.parse("2027-03-12"));
        assertThat(withoutRepetition.openEndedOccurrenceDatesFor(withoutSuspension, horizon)).containsExactly(LocalDate.parse("2027-03-12"));
        assertThat(anchoredOnTheSuspension.openEndedOccurrenceDatesFor(withoutSuspension, horizon)).isEmpty();
    }

    @Test
    @Tag("D-413")
    void changingThePaymentStatusKeepsTheEnrollmentSuspensionAndTheLateIssuanceFlags() {
        ReminderInvoiceFact march = ReminderInvoiceFacts.installmentOfAnEnrollment(1L, "2027-03-01", "2027-03-11", true, true);
        ReminderInvoiceFact overdue = march.withPaymentStatus(InvoicePaymentStatus.OVERDUE);

        assertThat(overdue.paymentStatus()).isEqualTo(InvoicePaymentStatus.OVERDUE);
        assertThat(overdue.enrollmentSuspended()).isTrue();
        assertThat(overdue.issuedAfterPreviousRun()).isTrue();
        assertThat(ReminderInvoiceFacts.installment(1L, "2027-03-01", "2027-03-11").enrollmentSuspended()).isFalse();
        assertThat(ReminderInvoiceFacts.installment(1L, "2027-03-01", "2027-03-11").issuedAfterPreviousRun()).isFalse();
    }

    @Test
    @Tag("D-413")
    void anInvoiceIssuedBeforeThePreviousRunKeepsItsIssueDayNoticeOnItsOwnDay() {
        ReminderInvoiceFact march = ReminderInvoiceFacts.installmentOfAnEnrollment(1L, "2027-03-01", "2027-03-11", false, false);
        ReminderNotice onIssueDay = notice("issue", ReminderMilestone.ISSUANCE, 0, null);

        List<ReminderOccurrence> occurrences = ReminderOccurrenceExpander.expandOccurrencesUpTo(march, List.of(onIssueDay), LocalDate.parse("2027-03-20"));

        assertThat(datesOf(occurrences)).containsExactly(LocalDate.parse("2027-03-01"));
    }
}
