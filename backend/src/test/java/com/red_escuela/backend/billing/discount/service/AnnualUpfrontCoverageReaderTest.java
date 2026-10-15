package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-158")
@Tag("D-388")
class AnnualUpfrontCoverageReaderTest {
    private final PaymentPlanSelectionRepository repository = mock(PaymentPlanSelectionRepository.class);
    private final AnnualUpfrontCoverageReader reader = new AnnualUpfrontCoverageReader(repository);
    private final EnrollmentReference enrollment = EnrollmentReference.ofProgram(5L);

    private PaymentPlanSelection selection(PaymentPlanCadence cadence, YearMonth from, Integer scopeMonths) {
        PaymentPlanOption option = new PaymentPlanOption();
        option.setCadence(cadence);
        PaymentPlanSelection selection = new PaymentPlanSelection();
        selection.setPaymentPlanOption(option);
        selection.setEffectiveFrom(from);
        selection.setScopeMonths(scopeMonths);
        return selection;
    }

    @Test
    void aRangeOverlappingTheCoveredMonthsIsCoveredIncludingTheBorders() {
        when(repository.findByProgramEnrollmentIdOrderByEffectiveFrom(5L)).thenReturn(List.of(selection(PaymentPlanCadence.ANNUAL_UPFRONT, YearMonth.of(2027, 3), 10)));

        assertThat(reader.annualUpfrontCoversAnyMonthOf(enrollment, YearMonth.of(2027, 12), YearMonth.of(2028, 3))).isTrue();
        assertThat(reader.annualUpfrontCoversAnyMonthOf(enrollment, YearMonth.of(2027, 1), YearMonth.of(2027, 3))).isTrue();
        assertThat(reader.annualUpfrontCoversAnyMonthOf(enrollment, YearMonth.of(2028, 1), YearMonth.of(2028, 6))).isFalse();
        assertThat(reader.annualUpfrontCoversAnyMonthOf(enrollment, YearMonth.of(2026, 1), YearMonth.of(2027, 2))).isFalse();
    }

    @Test
    void theLastCoveredMonthIsTheTenthOfATenMonthScope() {
        when(repository.findByProgramEnrollmentIdOrderByEffectiveFrom(5L)).thenReturn(List.of(selection(PaymentPlanCadence.ANNUAL_UPFRONT, YearMonth.of(2027, 3), 10)));

        assertThat(reader.annualUpfrontCoversAnyMonthOf(enrollment, YearMonth.of(2027, 12), YearMonth.of(2027, 12))).isTrue();
        assertThat(reader.annualUpfrontCoversAnyMonthOf(enrollment, YearMonth.of(2028, 1), YearMonth.of(2028, 1))).isFalse();
    }

    @Test
    void monthlyPlansAndSelectionsWithoutScopeNeverCover() {
        when(repository.findByProgramEnrollmentIdOrderByEffectiveFrom(5L)).thenReturn(List.of(
                selection(PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 3), 10),
                selection(PaymentPlanCadence.ANNUAL_UPFRONT, YearMonth.of(2027, 3), null)));

        assertThat(reader.annualUpfrontCoversAnyMonthOf(enrollment, YearMonth.of(2027, 4), YearMonth.of(2027, 8))).isFalse();
    }

    @Test
    void aMissingRangeIsNeverCovered() {
        assertThat(reader.annualUpfrontCoversAnyMonthOf(enrollment, null, YearMonth.of(2027, 8))).isFalse();
        assertThat(reader.annualUpfrontCoversAnyMonthOf(enrollment, YearMonth.of(2027, 8), null)).isFalse();
    }
}
