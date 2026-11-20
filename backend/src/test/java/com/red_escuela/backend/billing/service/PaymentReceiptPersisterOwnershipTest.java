package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentReceiptPersisterOwnershipTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final BankTransferReceiptRepository receipts = mock(BankTransferReceiptRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final PaymentReceiptPersister persister = new PaymentReceiptPersister(invoices, receipts, students);

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

    @Test
    void theOwnerOfTheInvoiceCanPersistTheReceipt() {
        Invoice invoice = mock(Invoice.class);
        when(invoice.getId()).thenReturn(10L);
        when(invoice.isPayable()).thenReturn(true);
        when(invoices.findByIdForUpdate(10L)).thenReturn(Optional.of(invoice));
        when(invoices.findOwnerStudentIdByInvoiceId(10L)).thenReturn(Optional.of(7L));
        when(students.findById(7L)).thenReturn(Optional.of(new Student()));
        when(receipts.save(any(BankTransferReceipt.class))).thenAnswer(call -> call.getArgument(0));

        BankTransferReceipt saved = persister.persist(10L, 7L, "students/7/c.pdf");

        assertThat(saved.getFilePath()).isEqualTo("students/7/c.pdf");
    }

    @Test
    void assertUploadableRejectsAnInvoiceOfAnotherStudentAsNotFound() {
        Invoice invoice = mock(Invoice.class);
        when(invoice.getId()).thenReturn(10L);
        when(invoices.findById(10L)).thenReturn(Optional.of(invoice));
        when(invoices.findOwnerStudentIdByInvoiceId(10L)).thenReturn(Optional.of(8L));

        assertThatThrownBy(() -> persister.assertUploadable(10L, 7L)).isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void assertUploadableRejectsAMissingInvoiceAsNotFound() {
        when(invoices.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> persister.assertUploadable(99L, 7L)).isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void assertUploadableRejectsANonPayableInvoiceWithTheSameErrorAsPersist() {
        Invoice invoice = mock(Invoice.class);
        when(invoice.getId()).thenReturn(10L);
        when(invoice.isPayable()).thenReturn(false);
        when(invoice.getPaymentStatus()).thenReturn(InvoicePaymentStatus.PAID);
        when(invoices.findById(10L)).thenReturn(Optional.of(invoice));
        when(invoices.findOwnerStudentIdByInvoiceId(10L)).thenReturn(Optional.of(7L));
        when(invoices.findByIdForUpdate(10L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> persister.assertUploadable(10L, 7L)).isInstanceOf(InvoiceNotPayableException.class);
        Throwable fromPersist = org.assertj.core.api.Assertions.catchThrowable(() -> persister.persist(10L, 7L, "students/7/c.pdf"));
        assertThatThrownBy(() -> persister.assertUploadable(10L, 7L)).hasMessage(fromPersist.getMessage());
    }
}
