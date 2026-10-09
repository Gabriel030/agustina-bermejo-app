package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssuedChargeQueryService {
    private final InvoiceRepository invoiceRepository;

    public IssuedChargeQueryService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(readOnly = true)
    public List<Integer> findIssuedEnrollmentFeeSlots(Long templateId) {
        return invoiceRepository.findIssuedEnrollmentFeeSlotsByTemplateId(templateId);
    }

    @Transactional(readOnly = true)
    public List<YearMonth> findIssuedInstallmentPeriods(Long programId, YearMonth from, YearMonth to) {
        return invoiceRepository.findIssuedInstallmentPeriodsByProgramIdAndPeriodBetween(programId, from, to);
    }
}
