package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentReceiptPersisterOwnershipTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final PaymentReceiptPersister persister = new PaymentReceiptPersister(invoices, mock(BankTransferReceiptRepository.class), mock(StudentRepository.class));

    @Test
    void anInvoiceWithoutAResolvableOwnerIsNotFoundInsteadOfSkippingTheOwnershipCheck() {
        Invoice invoice = mock(Invoice.class);
        when(invoice.getId()).thenReturn(10L);
        when(invoices.findByIdForUpdate(10L)).thenReturn(Optional.of(invoice));
        when(invoices.findOwnerStudentIdByInvoiceId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> persister.persist(10L, 7L, "students/7/c.pdf")).isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void anInvoiceOfAnotherStudentIsNotFound() {
        Invoice invoice = mock(Invoice.class);
        when(invoice.getId()).thenReturn(10L);
        when(invoices.findByIdForUpdate(10L)).thenReturn(Optional.of(invoice));
        when(invoices.findOwnerStudentIdByInvoiceId(10L)).thenReturn(Optional.of(8L));

        assertThatThrownBy(() -> persister.persist(10L, 7L, "students/7/c.pdf")).isInstanceOf(InvoiceNotFoundException.class);
    }
}
