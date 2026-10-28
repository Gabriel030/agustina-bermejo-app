package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-283")
class LostBenefitFilterTest {
    private static final YearMonth FEBRUARY = YearMonth.of(2031, 2);

    @Test
    void theUnfilteredReportAdmitsEverything() {
        assertThat(LostBenefitFilter.unfiltered().admits(1L, InvoiceType.MONTHLY_INSTALLMENT, FEBRUARY)).isTrue();
    }

    @Test
    void theDefinitionAndFeeTypeMustMatchWhenGiven() {
        LostBenefitFilter filter = new LostBenefitFilter(1L, InvoiceType.ENROLLMENT_FEE, null, null);

        assertThat(filter.admits(1L, InvoiceType.ENROLLMENT_FEE, FEBRUARY)).isTrue();
        assertThat(filter.admits(2L, InvoiceType.ENROLLMENT_FEE, FEBRUARY)).isFalse();
        assertThat(filter.admits(1L, InvoiceType.MONTHLY_INSTALLMENT, FEBRUARY)).isFalse();
    }

    @Test
    void theCutOffRangeIsInclusiveOnBothEnds() {
        LostBenefitFilter filter = new LostBenefitFilter(null, null, FEBRUARY, FEBRUARY.plusMonths(1));

        assertThat(filter.admits(1L, InvoiceType.MONTHLY_INSTALLMENT, FEBRUARY.minusMonths(1))).isFalse();
        assertThat(filter.admits(1L, InvoiceType.MONTHLY_INSTALLMENT, FEBRUARY)).isTrue();
        assertThat(filter.admits(1L, InvoiceType.MONTHLY_INSTALLMENT, FEBRUARY.plusMonths(1))).isTrue();
        assertThat(filter.admits(1L, InvoiceType.MONTHLY_INSTALLMENT, FEBRUARY.plusMonths(2))).isFalse();
    }
}
