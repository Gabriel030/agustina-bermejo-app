package com.red_escuela.backend.billing.run.domain;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-513")
class DraftInvoiceGroupTest {

    private static final DraftInvoiceGroupKey KEY = new DraftInvoiceGroupKey(MonthlyInvoiceEnrollmentType.PROGRAM, "Tecnicatura", FeeType.MONTHLY_INSTALLMENT, Currency.ARS);

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    @Test
    void tenDraftsForOneMillionAgainstNineHundredThousandIssuedLastMonthDifferByOneHundredThousandAndElevenPointElevenPercent() {
        DraftInvoiceGroup group = new DraftInvoiceGroup(KEY, 10, ars("1000000"), ars("900000"));

        assertThat(group.findDifferenceFromPreviousPeriod()).contains(ars("100000"));
        assertThat(group.findPercentageDifferenceFromPreviousPeriod().orElseThrow().points()).isEqualTo(new BigDecimal("11.11"));
    }

    @Test
    void aGroupWithoutPreviousPeriodHasNoDifferenceAndNoPercentage() {
        DraftInvoiceGroup group = new DraftInvoiceGroup(KEY, 10, ars("1000000"), null);

        assertThat(group.findPreviousIssuedTotal()).isEmpty();
        assertThat(group.findDifferenceFromPreviousPeriod()).isEmpty();
        assertThat(group.findPercentageDifferenceFromPreviousPeriod()).isEmpty();
    }

    @Test
    void draftsBelowThePreviousPeriodGiveANegativeDifferenceAndPercentage() {
        DraftInvoiceGroup group = new DraftInvoiceGroup(KEY, 3, ars("750"), ars("1000"));

        assertThat(group.findDifferenceFromPreviousPeriod()).contains(ars("-250"));
        assertThat(group.findPercentageDifferenceFromPreviousPeriod().orElseThrow().points()).isEqualTo(new BigDecimal("-25.00"));
    }

    @Test
    void aZeroPreviousTotalKeepsTheAbsoluteDifferenceButHasNoPercentage() {
        DraftInvoiceGroup group = new DraftInvoiceGroup(KEY, 3, ars("750"), ars("0"));

        assertThat(group.findDifferenceFromPreviousPeriod()).contains(ars("750"));
        assertThat(group.findPercentageDifferenceFromPreviousPeriod()).isEmpty();
    }
}
