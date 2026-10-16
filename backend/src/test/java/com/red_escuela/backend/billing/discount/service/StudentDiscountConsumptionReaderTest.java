package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.repository.AppliedInvoiceDiscountRepository;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-405")
class StudentDiscountConsumptionReaderTest {

    private final AppliedInvoiceDiscountRepository repository = mock(AppliedInvoiceDiscountRepository.class);
    private final StudentDiscountConsumptionReader reader = new StudentDiscountConsumptionReader(repository);
    private final StudentDiscount discount = mock(StudentDiscount.class);

    @Test
    void aDiscountWithAnAppliedUnreleasedInvoiceDiscountIsConsumed() {
        when(discount.getId()).thenReturn(7L);
        when(discount.getDiscountType()).thenReturn(DiscountType.INDIVIDUAL);
        when(repository.existsByStudentDiscountIdAndDiscountTypeAndConsumptionReleasedAtIsNull(7L, DiscountType.INDIVIDUAL)).thenReturn(true);

        assertThat(reader.isAlreadyConsumed(discount)).isTrue();
    }

    @Test
    void aDiscountWithoutAnAppliedInvoiceDiscountIsNotConsumed() {
        when(discount.getId()).thenReturn(7L);
        when(discount.getDiscountType()).thenReturn(DiscountType.INDIVIDUAL);
        when(repository.existsByStudentDiscountIdAndDiscountTypeAndConsumptionReleasedAtIsNull(7L, DiscountType.INDIVIDUAL)).thenReturn(false);

        assertThat(reader.isAlreadyConsumed(discount)).isFalse();
    }
}
