package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.domain.EnrollmentSuspensionRule;
import com.red_escuela.backend.billing.collections.repository.EnrollmentDebtFactsQuery;
import com.red_escuela.backend.billing.collections.service.EnrollmentPaymentStandingService;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class EnrollmentPaymentStandingStep {
    private static final Logger log = LoggerFactory.getLogger(EnrollmentPaymentStandingStep.class);

    public record Result(int candidates, int processed, int failed) {
    }

    private final EnrollmentDebtFactsQuery factsQuery;
    private final EnrollmentPaymentStandingService standingService;

    public EnrollmentPaymentStandingStep(EnrollmentDebtFactsQuery factsQuery, EnrollmentPaymentStandingService standingService) {
        this.factsQuery = factsQuery;
        this.standingService = standingService;
    }

    public Result evaluateActiveEnrollments(LocalDate businessDate, LatePaymentTerms terms) {
        EnrollmentSuspensionRule rule = EnrollmentSuspensionRule.from(terms);
        int candidates = 0;
        int processed = 0;
        int failed = 0;
        for (EnrollmentKind kind : EnrollmentKind.values()) {
            for (Long enrollmentId : factsQuery.findActiveEnrollmentIds(kind)) {
                candidates++;
                try {
                    standingService.evaluateEnrollmentStanding(kind, enrollmentId, businessDate, rule);
                    processed++;
                } catch (RuntimeException failure) {
                    failed++;
                    log.warn("Falló el estado de cobranza de la inscripción: tipo={}, inscripcion={}", kind, enrollmentId, failure);
                }
            }
        }
        log.info("Estado de cobranza evaluado: fecha={}, candidatas={}, procesadas={}, fallidas={}", businessDate, candidates, processed, failed);
        return new Result(candidates, processed, failed);
    }
}
