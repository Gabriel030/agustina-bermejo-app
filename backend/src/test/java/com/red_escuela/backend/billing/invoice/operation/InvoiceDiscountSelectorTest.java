package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountCandidateOrigin;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.candidate;
import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.studentDiscountCandidate;
import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.withOrigin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-073")
@Tag("D-295")
class InvoiceDiscountSelectorTest {

    private final InvoiceDiscountCalculator calculator = new InvoiceDiscountCalculator(new InvoiceIssuingOperationConfiguration().invoiceDiscountSelectionPolicySet());
    private final InvoiceDiscountCandidateStrategy legacyPromotion = mock(InvoiceDiscountCandidateStrategy.class);
    private final InvoiceDiscountCandidateStrategy studentDiscount = mock(InvoiceDiscountCandidateStrategy.class);
    private final InvoiceIssuingContext context = IssuingTestData.context(IssuingTestData.monthly(), Currency.ARS, null, null);

    private InvoiceDiscountSelector selector() {
        when(legacyPromotion.origin()).thenReturn(DiscountCandidateOrigin.LEGACY_PROMOTION);
        when(studentDiscount.origin()).thenReturn(DiscountCandidateOrigin.STUDENT_DISCOUNT);
        return new InvoiceDiscountSelector(List.of(legacyPromotion, studentDiscount), calculator);
    }

    private InvoiceDiscountCandidate legacyPromotionCandidate(String discount) {
        return withOrigin(candidate(DiscountType.PROMOTION, 1, InvoiceType.MONTHLY_INSTALLMENT, "100.00", discount), DiscountCandidateOrigin.LEGACY_PROMOTION);
    }

    private InvoiceDiscountCandidate studentScholarshipCandidate(String discount) {
        return studentDiscountCandidate(DiscountType.SCHOLARSHIP, 9, 9, discount, 1, Instant.parse("2027-02-01T00:00:00Z"));
    }

    @Test
    void aLegacyPromotionAndAStudentDiscountNeverStackAndAnEqualSavingGoesToTheOneWithPriorityAndGrantDate() {
        InvoiceDiscountSelector selector = selector();
        InvoiceDiscountCandidate legacy = legacyPromotionCandidate("30.00");
        InvoiceDiscountCandidate fromStudentDiscount = studentScholarshipCandidate("30.00");
        when(legacyPromotion.findDiscountCandidatesForInvoice(context)).thenReturn(List.of(legacy));
        when(studentDiscount.findDiscountCandidatesForInvoice(context)).thenReturn(List.of(fromStudentDiscount));

        selector.selectDiscountsWithoutLock(context);

        assertThat(context.winningDiscounts()).containsExactly(Map.entry(1, fromStudentDiscount));
        assertThat(context.discountCandidates()).containsExactlyInAnyOrder(legacy, fromStudentDiscount);
    }

    @Test
    void theReselectionRequestedByTheContextAsksEveryStrategyForItsCandidatesUnderTheEnrollmentLockAndReplacesTheEarlierSelection() {
        InvoiceDiscountSelector selector = selector();
        InvoiceDiscountCandidate beforeTheLock = studentScholarshipCandidate("50.00");
        when(legacyPromotion.findDiscountCandidatesForInvoice(context)).thenReturn(List.of());
        when(studentDiscount.findDiscountCandidatesForInvoice(context)).thenReturn(List.of(beforeTheLock));
        InvoiceDiscountCandidate afterTheLock = studentScholarshipCandidate("20.00");
        when(legacyPromotion.findDiscountCandidatesUnderEnrollmentLock(context)).thenReturn(List.of());
        when(studentDiscount.findDiscountCandidatesUnderEnrollmentLock(context)).thenReturn(List.of(afterTheLock));

        selector.selectDiscountsWithoutLock(context);
        assertThat(context.winningDiscounts()).hasSize(1);

        context.reselectDiscounts();

        assertThat(context.winningDiscounts()).containsExactly(Map.entry(1, afterTheLock));
        assertThat(context.discountCandidates()).containsExactly(afterTheLock);
        verify(studentDiscount).findDiscountCandidatesUnderEnrollmentLock(context);
        verify(legacyPromotion).findDiscountCandidatesUnderEnrollmentLock(context);
    }
}
