package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceAllocationQueryService {

    private final InvoiceRepository invoiceRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Invoice> listOpenInvoicesOfAccount(Long studentCurrentAccountId) {
        return invoiceRepository.findOpenIssuedInvoicesByAccountOrderedForAllocation(studentCurrentAccountId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Invoice> findInvoicesByIds(java.util.Collection<Long> invoiceIds) {
        return invoiceRepository.findByIdIn(invoiceIds);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockOpenInvoicesOfAccountInIdOrder(Long studentCurrentAccountId) {
        invoiceRepository.findOpenIssuedInvoiceIdsByAccountOrderedById(studentCurrentAccountId).forEach(invoiceRepository::lockInvoiceRowById);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Invoice lockInvoice(Long invoiceId) {
        return invoiceRepository.findByIdForUpdate(invoiceId).orElseThrow();
    }
}
