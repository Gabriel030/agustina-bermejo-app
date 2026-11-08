package com.red_escuela.backend.billing.advanceenrollmentfee.service;

import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeExpiryResult.Failure;
import com.red_escuela.backend.billing.configuration.service.AdvanceEnrollmentFeeCalendarReader;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AdvanceEnrollmentFeeExpirer {
    public static final String EXPIRY_FAILED = "ADVANCE_ENROLLMENT_FEE_EXPIRY_FAILED";
    public static final String CALENDAR_FAILED = "ADVANCE_ENROLLMENT_FEE_CALENDAR_FAILED";
    private static final int MAX_MESSAGE_LENGTH = 500;
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
        int expired = 0;
        int skipped = 0;
        List<Failure> failures = new ArrayList<>();
        for (Long templateId : invoiceRepository.findTemplateIdsOfUnpaidAdvanceEnrollmentFees()) {
            Optional<LocalDate> closesOn;
            try {
                closesOn = calendarReader.windowClosesOnOfTemplate(templateId, businessDate);
            } catch (RuntimeException failure) {
                log.error("No se pudo leer el calendario de la matrícula anticipada de la plantilla {}", templateId, failure);
                invoiceRepository.findUnpaidAdvanceEnrollmentFeeIdsByTemplateId(templateId)
                        .forEach(invoiceId -> failures.add(new Failure(invoiceId, CALENDAR_FAILED, describe(failure))));
                continue;
            }
            if (closesOn.isEmpty()) {
                int withoutCalendar = invoiceRepository.findUnpaidAdvanceEnrollmentFeeIdsByTemplateId(templateId).size();
                log.warn("Plantilla {} sin calendario de matrícula anticipada vigente: {} facturas omitidas", templateId, withoutCalendar);
                skipped += withoutCalendar;
                continue;
            }
            if (!closesOn.get().isBefore(businessDate)) {
                continue;
            }
            for (Long invoiceId : invoiceRepository.findUnpaidAdvanceEnrollmentFeeIdsByTemplateId(templateId)) {
                try {
                    if (expiration.cancelWhenWindowClosedAndUnpaid(invoiceId)) {
                        expired++;
                    } else {
                        skipped++;
                    }
                } catch (RuntimeException failure) {
                    log.error("No se pudo vencer la matrícula anticipada {}", invoiceId, failure);
                    failures.add(new Failure(invoiceId, EXPIRY_FAILED, describe(failure)));
                }
            }
        }
        AdvanceEnrollmentFeeExpiryResult result = new AdvanceEnrollmentFeeExpiryResult(expired, skipped, failures);
        log.info("Vencimiento de matrículas anticipadas finalizado: fecha={}, candidatas={}, anuladas={}, fallidas={}", businessDate, result.candidates(), expired, failures.size());
        return result;
    }

    private static String describe(RuntimeException failure) {
        String detail = failure.getMessage() == null ? "" : ": " + failure.getMessage();
        String text = failure.getClass().getSimpleName() + detail;
        return text.length() > MAX_MESSAGE_LENGTH ? text.substring(0, MAX_MESSAGE_LENGTH) : text;
    }
}
