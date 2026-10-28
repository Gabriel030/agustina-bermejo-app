package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.domain.EnrollmentSuspensionRule;
import com.red_escuela.backend.billing.collections.repository.EnrollmentDebtFactsQuery;
import com.red_escuela.backend.billing.collections.service.EnrollmentPaymentStandingService;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

@Component
public class EnrollmentPaymentStandingStep {
    private static final Logger log = LoggerFactory.getLogger(EnrollmentPaymentStandingStep.class);

    public record Result(int candidates, int processed, int failed) {
    }

    private final EnrollmentDebtFactsQuery factsQuery;
    private final EnrollmentPaymentStandingService standingService;
    private final BillingConfigurationReader configurationReader;

    public EnrollmentPaymentStandingStep(EnrollmentDebtFactsQuery factsQuery, EnrollmentPaymentStandingService standingService,
                                         BillingConfigurationReader configurationReader) {
        this.factsQuery = factsQuery;
        this.standingService = standingService;
        this.configurationReader = configurationReader;
    }

    public Result evaluateEnrollments(LocalDate businessDate, LatePaymentTerms termsOfTheDay) {
        LocalDate cutoff = YearMonth.from(businessDate).minusMonths(1).atEndOfMonth();
        EnrollmentSuspensionRule ruleAtCutoff = configurationReader.findLatePaymentSettingsEffectiveOn(cutoff)
                .map(EnrollmentSuspensionRule::from)
                .orElseGet(() -> EnrollmentSuspensionRule.from(termsOfTheDay));
        int candidates = 0;
        int processed = 0;
        int failed = 0;
        for (EnrollmentKind kind : EnrollmentKind.values()) {
            for (Long enrollmentId : factsQuery.findStepCandidateEnrollmentIds(kind)) {
                candidates++;
                try {
                    standingService.evaluateEnrollmentStanding(kind, enrollmentId, businessDate, ruleAtCutoff);
                    processed++;
                } catch (RuntimeException failure) {
                    failed++;
                    log.warn("Falló el estado de cobranza de la inscripción: tipo={}, inscripcion={}", kind, enrollmentId, failure);
                }
            }
        }
        log.info("Estado de cobranza evaluado: fecha={}, corte={}, candidatas={}, procesadas={}, fallidas={}", businessDate, cutoff, candidates, processed, failed);
        return new Result(candidates, processed, failed);
    }
}
