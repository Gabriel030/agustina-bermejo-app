package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.EnrollmentDebtFacts;
import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.domain.EnrollmentPaymentEvent;
import com.red_escuela.backend.billing.collections.domain.EnrollmentPaymentStanding;
import com.red_escuela.backend.billing.collections.domain.EnrollmentPaymentStandingEvaluator;
import com.red_escuela.backend.billing.collections.domain.EnrollmentSuspensionRule;
import com.red_escuela.backend.billing.collections.domain.EnrollmentSuspensionRuleTimeline;
import com.red_escuela.backend.billing.collections.domain.MonthCloseFacts;
import com.red_escuela.backend.billing.collections.repository.EnrollmentDebtFactsQuery;
import com.red_escuela.backend.billing.collections.repository.EnrollmentPaymentStandingRepository;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
public class EnrollmentPaymentStandingService {
    public static final String STANDING_PROCESS = "ENROLLMENT_PAYMENT_STANDING";
    public static final String LEGACY_TERM_CLOSURE_PROCESS = "LEGACY_TERM_CLOSURE";

    private static final Logger log = LoggerFactory.getLogger(EnrollmentPaymentStandingService.class);
    private static final StatusChangeActor STANDING_ACTOR = StatusChangeActor.ofProcess(STANDING_PROCESS);
    private static final EnrollmentSuspensionRule UNUSED_OUTSIDE_MONTH_CLOSE = new EnrollmentSuspensionRule(1, 0);

    private final EnrollmentPaymentStandingRepository standingRepository;
    private final EnrollmentDebtFactsQuery factsQuery;
    private final EnrollmentMonthCloseFactsLoader closeFactsLoader;

    public EnrollmentPaymentStandingService(EnrollmentPaymentStandingRepository standingRepository, EnrollmentDebtFactsQuery factsQuery,
                                            EnrollmentMonthCloseFactsLoader closeFactsLoader) {
        this.standingRepository = standingRepository;
        this.factsQuery = factsQuery;
        this.closeFactsLoader = closeFactsLoader;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void evaluateEnrollmentStanding(EnrollmentKind kind, Long enrollmentId, LocalDate businessDate, EnrollmentSuspensionRuleTimeline rules) {
        EnrollmentPaymentStanding standing = lockStandingOf(kind, enrollmentId).orElseGet(() -> openStandingOf(kind, enrollmentId, businessDate));
        if (standing.getStatus() == EnrollmentPaymentStatus.SUSPENDED && factsQuery.isEnrollmentWithdrawnForNonpayment(kind, enrollmentId)) {
            closeSuspendedStanding(standing, businessDate);
            return;
        }
        YearMonth lastClosedMonth = YearMonth.from(businessDate).minusMonths(1);
        if (!standing.isMonthCloseDue(lastClosedMonth)) {
            applyEvaluation(standing, businessDate, null, UNUSED_OUTSIDE_MONTH_CLOSE);
        } else if (!factsQuery.isEnrollmentActive(kind, enrollmentId)) {
            standing.markMonthCloseEvaluated(lastClosedMonth);
            applyEvaluation(standing, businessDate, null, UNUSED_OUTSIDE_MONTH_CLOSE);
        } else {
            for (YearMonth month = standing.lastClosedMonth().plusMonths(1); !month.isAfter(lastClosedMonth); month = month.plusMonths(1)) {
                LocalDate cutoff = month.atEndOfMonth();
                MonthCloseFacts closeFacts = closeFactsLoader.loadFactsAtCutoff(kind, enrollmentId, cutoff);
                standing.markMonthCloseEvaluated(month);
                applyEvaluation(standing, businessDate, closeFacts, rules.ruleAtCutoff(cutoff));
            }
        }
        standingRepository.save(standing);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reevaluateExistingStanding(EnrollmentKind kind, Long enrollmentId, LocalDate businessDate) {
        lockStandingOf(kind, enrollmentId).ifPresent(standing -> {
            if (standing.getStatus() == EnrollmentPaymentStatus.SUSPENDED && factsQuery.isEnrollmentWithdrawnForNonpayment(kind, enrollmentId)) {
                closeSuspendedStanding(standing, businessDate);
                return;
            }
            applyEvaluation(standing, businessDate, null, UNUSED_OUTSIDE_MONTH_CLOSE);
            standingRepository.save(standing);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordTermClosedWhileSuspended(EnrollmentKind kind, Long enrollmentId, LocalDate businessDate) {
        Optional<EnrollmentPaymentStanding> standing = lockStandingOf(kind, enrollmentId);
        if (standing.isEmpty() || standing.get().getStatus() != EnrollmentPaymentStatus.SUSPENDED) {
            log.info("El cierre del período dio de baja por mora a una inscripción que el estado de cobranza nuevo no tiene suspendida: tipo={}, inscripcion={}, estado={}",
                    kind, enrollmentId, standing.map(s -> s.getStatus().name()).orElse("sin estado"));
            return;
        }
        closeSuspendedStanding(standing.get(), businessDate);
    }

    private void closeSuspendedStanding(EnrollmentPaymentStanding standing, LocalDate businessDate) {
        standing.applyEvent(EnrollmentPaymentEvent.TERM_CLOSED_WHILE_SUSPENDED, businessDate, StatusChangeActor.ofProcess(LEGACY_TERM_CLOSURE_PROCESS));
        standingRepository.save(standing);
        log.info("Estado de cobranza: inscripcion dada de baja por mora, tipo={}, inscripcion={}", standing.kind(), standing.enrollmentId());
    }

    private void applyEvaluation(EnrollmentPaymentStanding standing, LocalDate businessDate, MonthCloseFacts closeFacts, EnrollmentSuspensionRule rule) {
        EnrollmentDebtFacts facts = factsQuery.findLiveFactsOf(standing.kind(), standing.enrollmentId());
        List<EnrollmentPaymentEvent> events = EnrollmentPaymentStandingEvaluator.evaluateStanding(standing.getStatus(), facts, closeFacts, rule);
        for (EnrollmentPaymentEvent event : events) {
            EnrollmentPaymentStatus before = standing.getStatus();
            standing.applyEvent(event, businessDate, STANDING_ACTOR);
            log.info("Estado de cobranza: tipo={}, inscripcion={}, evento={}, de={}, a={}", standing.kind(), standing.enrollmentId(), event, before, standing.getStatus());
        }
    }

    private Optional<EnrollmentPaymentStanding> lockStandingOf(EnrollmentKind kind, Long enrollmentId) {
        return kind == EnrollmentKind.PROGRAM
                ? standingRepository.findByProgramEnrollmentForUpdate(enrollmentId)
                : standingRepository.findByCourseEnrollmentForUpdate(enrollmentId);
    }

    private EnrollmentPaymentStanding openStandingOf(EnrollmentKind kind, Long enrollmentId, LocalDate businessDate) {
        Long studentId = factsQuery.findStudentIdOf(kind, enrollmentId)
                .orElseThrow(() -> new IllegalStateException("The enrollment has no student: " + kind + " " + enrollmentId));
        return EnrollmentPaymentStanding.open(kind, enrollmentId, studentId, businessDate, STANDING_ACTOR);
    }
}
