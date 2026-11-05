package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class InvoiceAllocationQueryService {

    private final InvoiceRepository invoiceRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Invoice> listOpenInvoicesOfAccount(Long studentCurrentAccountId) {
        return invoiceRepository.findOpenIssuedInvoicesByAccountOrderedForAllocation(studentCurrentAccountId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Invoice> listInvoicesAllocatableToAccountCredit(Long studentCurrentAccountId, Set<Long> underReviewInvoiceIdsThatMayBePaid) {
        return invoiceRepository.findOpenIssuedInvoicesByAccountOrderedForAllocation(studentCurrentAccountId).stream()
                .filter(invoice -> invoice.getPaymentStatus() != InvoicePaymentStatus.PAYMENT_UNDER_REVIEW
                        || underReviewInvoiceIdsThatMayBePaid.contains(invoice.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Invoice> listOpenInvoicesOfAccountForAllocationPreview(Long studentCurrentAccountId) {
        return invoiceRepository.findOpenIssuedInvoicesByAccountOrderedForAllocation(studentCurrentAccountId);
    }

    @Transactional(readOnly = true)
    public Set<Long> findInvoiceIdsAwaitingAuthorization(Collection<Long> invoiceIds) {
        return invoiceIds.isEmpty() ? Set.of() : Set.copyOf(invoiceRepository.findInvoiceIdsWithPendingAuthorizationRequest(invoiceIds));
    }

    @Transactional(readOnly = true)
    public List<Invoice> findInvoicesForPreview(Collection<Long> invoiceIds) {
        return invoiceRepository.findByIdIn(invoiceIds);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Invoice> findInvoicesByIds(Collection<Long> invoiceIds) {
        return invoiceRepository.findByIdIn(invoiceIds);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockOpenInvoicesOfAccountInIdOrder(Long studentCurrentAccountId) {
        invoiceRepository.findOpenIssuedInvoiceIdsByAccountOrderedById(studentCurrentAccountId).forEach(invoiceRepository::lockInvoiceRowById);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockOpenInvoicesOfAccountAndTheseInIdOrder(Long studentCurrentAccountId, Collection<Long> alsoInvoiceIds) {
        Set<Long> ascendingInvoiceIds = new TreeSet<>(invoiceRepository.findOpenIssuedInvoiceIdsByAccountOrderedById(studentCurrentAccountId));
        ascendingInvoiceIds.addAll(alsoInvoiceIds);
        ascendingInvoiceIds.forEach(invoiceRepository::lockInvoiceRowById);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Invoice lockInvoice(Long invoiceId) {
        return invoiceRepository.findByIdForUpdate(invoiceId).orElseThrow();
    }
}
