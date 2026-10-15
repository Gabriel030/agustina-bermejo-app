package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.billing.account.domain.PaymentAllocationSource;
import com.red_escuela.backend.billing.account.repository.PaymentAllocationSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AllocationSourceQueryService {

    private final PaymentAllocationSourceRepository sourceRepository;

    public AllocationSourceQueryService(PaymentAllocationSourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, List<PaymentAllocationSource>> findCurrentSourcesByInvoiceId(Collection<Long> invoiceIds) {
        if (invoiceIds.isEmpty()) {
            return Map.of();
        }
        return sourceRepository.findCurrentSourcesWithTheirInvoiceId(invoiceIds).stream().collect(Collectors.groupingBy(
                row -> (Long) row[0], Collectors.mapping(row -> (PaymentAllocationSource) row[1], Collectors.toList())));
    }
}
