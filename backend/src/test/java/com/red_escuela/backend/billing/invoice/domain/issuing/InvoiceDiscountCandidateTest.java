package com.red_escuela.backend.billing.invoice.domain.issuing;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.ars;
import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.candidateOnLine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-301")
class InvoiceDiscountCandidateTest {

    @Test
    void aLineNumberBelowOneIsRejected() {
        assertThatThrownBy(() -> candidateOnLine(0, DiscountType.COHORT_PROMOTION, 1, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "10.00"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> candidateOnLine(-1, DiscountType.COHORT_PROMOTION, 1, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "10.00"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theFirstLineNumberIsAccepted() {
        assertThat(candidateOnLine(1, DiscountType.COHORT_PROMOTION, 1, InvoiceType.MONTHLY_INSTALLMENT, "100.00", "10.00").lineNumber()).isEqualTo(1);
    }

    @Test
    void aCandidateWithoutOriginIsRejected() {
        assertThatThrownBy(() -> new InvoiceDiscountCandidate(DiscountType.COHORT_PROMOTION, 1L, 1L, "d", InvoiceType.MONTHLY_INSTALLMENT, ars("100.00"), ars("10.00"),
                null, null, 1, null, null, null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void aLegacySourceCandidateStartsOnTheFirstLineWithoutPriorityGrantOrCalculation() {
        var legacy = InvoiceDiscountCandidate.ofLegacySource(DiscountCandidateOrigin.COHORT_ENTRY, DiscountType.COHORT_PROMOTION, 1L, 1L, "d",
                InvoiceType.MONTHLY_INSTALLMENT, ars("100.00"), ars("10.00"), null, null);

        assertThat(legacy.lineNumber()).isEqualTo(1);
        assertThat(legacy.priority()).isNull();
        assertThat(legacy.grantedAt()).isNull();
        assertThat(legacy.calculation()).isNull();
        assertThat(legacy.origin()).isEqualTo(DiscountCandidateOrigin.COHORT_ENTRY);
    }
}
