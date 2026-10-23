package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountCandidateOrigin;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.candidate;
import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.studentDiscountCandidate;
import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.withOrigin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-073")
class InvoiceDiscountStrategiesByOriginTest {

    private final InvoiceDiscountCandidateStrategy cohortEntry = mock(InvoiceDiscountCandidateStrategy.class);
    private final InvoiceDiscountCandidateStrategy studentDiscount = mock(InvoiceDiscountCandidateStrategy.class);

    private InvoiceDiscountStrategiesByOrigin strategies() {
        when(cohortEntry.origin()).thenReturn(DiscountCandidateOrigin.COHORT_ENTRY);
        when(studentDiscount.origin()).thenReturn(DiscountCandidateOrigin.STUDENT_DISCOUNT);
        return new InvoiceDiscountStrategiesByOrigin(List.of(cohortEntry, studentDiscount));
    }

    private InvoiceDiscountCandidate cohortEntryWinner() {
        return withOrigin(candidate(DiscountType.COHORT_PROMOTION, 1, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "50.00"), DiscountCandidateOrigin.COHORT_ENTRY);
    }

    private InvoiceDiscountCandidate studentScholarshipWinner() {
        return studentDiscountCandidate(DiscountType.SCHOLARSHIP, 9, 9, "50.00", 1, Instant.parse("2027-02-01T00:00:00Z"));
    }

    @Test
    void markingAsAppliedReachesTheStrategyThatProducedTheWinner() {
        InvoiceDiscountCandidate legacy = cohortEntryWinner();

        strategies().markWinnersAsApplied(List.of(legacy));

        verify(cohortEntry).markDiscountAsApplied(legacy);
        verify(studentDiscount, never()).markDiscountAsApplied(legacy);
    }

    @Test
    void aStudentDiscountWinnerIsMarkedOnlyOnTheStudentDiscountStrategyEvenWhenBothAreScholarships() {
        InvoiceDiscountCandidate fromStudentDiscount = studentScholarshipWinner();

        strategies().markWinnersAsApplied(List.of(fromStudentDiscount));

        verify(studentDiscount).markDiscountAsApplied(fromStudentDiscount);
        verify(cohortEntry, never()).markDiscountAsApplied(fromStudentDiscount);
    }

    @Test
    void twoStrategiesDeclaringTheSameOriginAreRejectedAtStartup() {
        InvoiceDiscountCandidateStrategy first = mock(InvoiceDiscountCandidateStrategy.class);
        InvoiceDiscountCandidateStrategy second = mock(InvoiceDiscountCandidateStrategy.class);
        when(first.origin()).thenReturn(DiscountCandidateOrigin.COHORT_ENTRY);
        when(second.origin()).thenReturn(DiscountCandidateOrigin.COHORT_ENTRY);

        assertThatThrownBy(() -> new InvoiceDiscountStrategiesByOrigin(List.of(first, second))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void everyRegisteredStrategyIsReturned() {
        assertThat(strategies().all()).containsExactlyInAnyOrder(cohortEntry, studentDiscount);
    }
}
