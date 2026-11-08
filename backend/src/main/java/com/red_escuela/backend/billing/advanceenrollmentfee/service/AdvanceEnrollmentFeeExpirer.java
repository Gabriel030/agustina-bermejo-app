package com.red_escuela.backend.billing.advanceenrollmentfee.service;

import com.red_escuela.backend.billing.configuration.service.AdvanceEnrollmentFeeCalendarReader;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdvanceEnrollmentFeeExpirer {
    private static final Logger log = LoggerFactory.getLogger(AdvanceEnrollmentFeeExpirer.class);

    private final InvoiceRepository invoiceRepository;
    private final AdvanceEnrollmentFeeCalendarReader calendarReader;
    private final AdvanceEnrollmentFeeExpiration expiration;

    public AdvanceEnrollmentFeeExpirer(InvoiceRepository invoiceRepository, AdvanceEnrollmentFeeCalendarReader calendarReader, AdvanceEnrollmentFeeExpiration expiration) {
        this.invoiceRepository = invoiceRepository;
        this.calendarReader = calendarReader;
        this.expiration = expiration;
    }

    public AdvanceEnrollmentFeeExpiryResult expireFeesWhoseWindowClosedBefore(LocalDate businessDate) {
        int candidates = 0;
        int expired = 0;
        int skipped = 0;
        List<Long> failed = new ArrayList<>();
        for (Long templateId : invoiceRepository.findTemplateIdsOfUnpaidAdvanceEnrollmentFees()) {
            if (!windowClosedBefore(templateId, businessDate)) {
                continue;
            }
            for (Long invoiceId : invoiceRepository.findUnpaidAdvanceEnrollmentFeeIdsByTemplateId(templateId)) {
                candidates++;
                try {
                    if (expiration.cancelWhenWindowClosedAndUnpaid(invoiceId)) {
                        expired++;
                    } else {
                        skipped++;
                    }
                } catch (RuntimeException failure) {
                    log.error("No se pudo vencer la matrícula anticipada {}", invoiceId, failure);
                    failed.add(invoiceId);
                }
            }
        }
        log.info("Vencimiento de matrículas anticipadas finalizado: fecha={}, candidatas={}, anuladas={}, fallidas={}", businessDate, candidates, expired, failed.size());
        return new AdvanceEnrollmentFeeExpiryResult(expired, skipped, failed);
    }

    private boolean windowClosedBefore(Long templateId, LocalDate businessDate) {
        return calendarReader.windowClosesOnOfTemplate(templateId).filter(closesOn -> closesOn.isBefore(businessDate)).isPresent();
    }
}
