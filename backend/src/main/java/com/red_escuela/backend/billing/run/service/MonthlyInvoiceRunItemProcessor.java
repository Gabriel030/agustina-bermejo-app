package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceEnrollmentType;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceItemOutcome;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItem;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemRepository;
import com.red_escuela.backend.billing.service.MonthlyInstallmentRowProcessor;
import com.red_escuela.backend.billing.service.MonthlyInstallmentRowProcessor.MonthlyIssuanceReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Service
public class MonthlyInvoiceRunItemProcessor {
    private static final Logger log = LoggerFactory.getLogger(MonthlyInvoiceRunItemProcessor.class);

    private final MonthlyInstallmentRowProcessor rowProcessor;
    private final MonthlyInvoiceRunItemRepository itemRepository;
    private final Clock clock;

    public MonthlyInvoiceRunItemProcessor(MonthlyInstallmentRowProcessor rowProcessor, MonthlyInvoiceRunItemRepository itemRepository, Clock clock) {
        this.rowProcessor = rowProcessor;
        this.itemRepository = itemRepository;
        this.clock = clock;
    }

    public MonthlyInvoiceRunItem issueAndRecordItem(Long runId, MonthlyInvoiceEnrollmentType type, Long enrollmentId, YearMonth period, LocalDate pricingDate) {
        MonthlyInvoiceItemOutcome outcome = issueEnrollmentMonth(type, enrollmentId, period, pricingDate);
        return persistItemIsolatingFailure(runId, type, enrollmentId, outcome);
    }

    private MonthlyInvoiceRunItem persistItemIsolatingFailure(Long runId, MonthlyInvoiceEnrollmentType type, Long enrollmentId, MonthlyInvoiceItemOutcome outcome) {
        try {
            return itemRepository.save(MonthlyInvoiceRunItem.recordItemOutcome(runId, type, enrollmentId, outcome, LocalDateTime.now(clock)));
        } catch (RuntimeException e) {
            log.error("Falló el registro del ítem de la corrida mensual: corrida={}, tipo={}, id={}", runId, type, enrollmentId, e);
            return recordPersistenceFailure(runId, type, enrollmentId, e);
        }
    }

    private MonthlyInvoiceRunItem recordPersistenceFailure(Long runId, MonthlyInvoiceEnrollmentType type, Long enrollmentId, RuntimeException cause) {
        MonthlyInvoiceRunItem failed = MonthlyInvoiceRunItem.recordItemOutcome(runId, type, enrollmentId,
                MonthlyInvoiceItemOutcome.failed(cause.getClass().getSimpleName(), cause.getMessage()), LocalDateTime.now(clock));
        try {
            return itemRepository.save(failed);
        } catch (RuntimeException e) {
            log.error("Tampoco se pudo registrar el ítem fallido: corrida={}, tipo={}, id={}", runId, type, enrollmentId, e);
            return failed;
        }
    }

    private MonthlyInvoiceItemOutcome issueEnrollmentMonth(MonthlyInvoiceEnrollmentType type, Long enrollmentId, YearMonth period, LocalDate pricingDate) {
        try {
            MonthlyIssuanceReport report = switch (type) {
                case PROGRAM -> rowProcessor.reportProgramEnrollmentIssuance(enrollmentId, period, pricingDate);
                case COURSE -> rowProcessor.reportCourseEnrollmentIssuance(enrollmentId, period, pricingDate);
            };
            return outcomeOf(report);
        } catch (Exception e) {
            log.error("Falló la emisión mensual de la inscripción: tipo={}, id={}", type, enrollmentId, e);
            return MonthlyInvoiceItemOutcome.failed(e.getClass().getSimpleName(), e.getMessage());
        }
    }

    static MonthlyInvoiceItemOutcome outcomeOf(MonthlyIssuanceReport report) {
        return report.issuedInvoiceIds().isEmpty() ? withoutNewInvoices(report) : MonthlyInvoiceItemOutcome.issued(report.issuedInvoiceIds());
    }

    private static MonthlyInvoiceItemOutcome withoutNewInvoices(MonthlyIssuanceReport report) {
        return report.alreadyIssuedInvoiceIds().isEmpty()
                ? MonthlyInvoiceItemOutcome.notApplicable(report.planningDecisionCodes())
                : MonthlyInvoiceItemOutcome.alreadyIssued(report.alreadyIssuedInvoiceIds());
    }
}
