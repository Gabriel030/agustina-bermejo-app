package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InvoiceCreditAccessService {

    private final InvoiceRepository invoiceRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Optional<Invoice> findInvoice(Long invoiceId) {
        return invoiceRepository.findInvoiceWithLinesById(invoiceId);
    }

    @Transactional
    public Optional<Invoice> lockInvoice(Long invoiceId) {
        Optional<Invoice> invoice = invoiceRepository.findByIdForUpdate(invoiceId);
        invoice.ifPresent(locked -> entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE));
        return invoice;
    }

    @Transactional(readOnly = true)
    public Optional<Long> findAccountIdOf(Long invoiceId) {
        return invoiceRepository.findStudentCurrentAccountIdByInvoiceId(invoiceId);
    }

    @Transactional(readOnly = true)
    public Optional<String> findAccountOwnerDocumentNumber(Long studentCurrentAccountId) {
        return invoiceRepository.findAccountOwnerDocumentNumber(studentCurrentAccountId);
    }

    @Transactional(readOnly = true)
    public Optional<Long> findAccountOwnerPersonId(Long invoiceId) {
        return invoiceRepository.findOwnerPersonByInvoiceId(invoiceId).map(person -> person.getId());
    }
}
