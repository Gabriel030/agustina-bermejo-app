package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class PendingInvoiceQueryService {
    private final InvoiceRepository invoiceRepository;

    public PendingInvoiceQueryService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(readOnly = true)
    public List<Long> findOverdueLateFeeOriginIdsAfter(Long afterInvoiceId, int pageSize) {
        return invoiceRepository.findOverdueLateFeeOriginIdsAfter(afterInvoiceId, PageRequest.of(0, pageSize));
    }

    @Transactional(readOnly = true)
    public List<Long> findPendingInvoiceIdsDueBefore(LocalDate date, Long afterInvoiceId, int pageSize) {
        return invoiceRepository.findByPaymentStatusAndDueDateBeforeAndIdGreaterThanOrderByIdAsc(
                InvoicePaymentStatus.PENDING, date, afterInvoiceId, PageRequest.of(0, pageSize)).stream().map(Invoice::getId).toList();
    }
}
