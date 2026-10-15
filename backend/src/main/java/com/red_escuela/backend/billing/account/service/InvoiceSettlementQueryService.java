package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.billing.account.domain.InvoiceSettlement;
import com.red_escuela.backend.billing.account.repository.PaymentAllocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class InvoiceSettlementQueryService {
    private final PaymentAllocationRepository allocationRepository;

    public InvoiceSettlementQueryService(PaymentAllocationRepository allocationRepository) {
        this.allocationRepository = allocationRepository;
    }

    @Transactional(readOnly = true)
    public Optional<InvoiceSettlement> findCurrentSettlementOfInvoice(Long invoiceId) {
        return allocationRepository.findCurrentAllocationByInvoiceId(invoiceId)
                .map(allocation -> new InvoiceSettlement(allocation.getId(), allocation.getEffectiveDate()));
    }
}
