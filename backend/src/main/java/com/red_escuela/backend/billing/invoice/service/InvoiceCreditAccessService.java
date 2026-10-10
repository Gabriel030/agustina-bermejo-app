package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import jakarta.persistence.EntityManager;
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
        Optional<Invoice> invoice = invoiceRepository.findInvoiceWithLinesById(invoiceId);
        invoice.ifPresent(entityManager::detach);
        return invoice;
    }

    @Transactional
    public Optional<Invoice> lockInvoice(Long invoiceId) {
        Optional<Invoice> invoice = invoiceRepository.findByIdForUpdate(invoiceId);
        invoice.ifPresent(locked -> entityManager.refresh(locked));
        return invoice;
    }

    @Transactional(readOnly = true)
    public Optional<Long> findAccountIdOf(Long invoiceId) {
        return invoiceRepository.findStudentCurrentAccountIdByInvoiceId(invoiceId);
    }

    @Transactional(readOnly = true)
    public Optional<Long> findAccountOwnerPersonId(Long invoiceId) {
        return invoiceRepository.findOwnerPersonByInvoiceId(invoiceId).map(person -> person.getId());
    }
}
