package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.EnrollmentClosedWhileSuspendedEvent;
import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.domain.SuspendedEnrollmentClosureReport;
import com.red_escuela.backend.billing.collections.repository.EnrollmentPaymentStandingRepository;
import com.red_escuela.backend.enrollment.api.CourseEnrollmentEndView;
import com.red_escuela.backend.enrollment.api.CourseEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.CourseEnrollmentNonpaymentWithdrawalPort;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CourseEnrollmentWithdrawalAtCourseEndService {

    private static final Logger log = LoggerFactory.getLogger(CourseEnrollmentWithdrawalAtCourseEndService.class);

    private final EnrollmentPaymentStandingRepository standingRepository;
    private final CourseEnrollmentFinder courseEnrollmentFinder;
    private final CourseEnrollmentNonpaymentWithdrawalPort courseEnrollmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final TransactionTemplate requiresNew;

    public CourseEnrollmentWithdrawalAtCourseEndService(EnrollmentPaymentStandingRepository standingRepository, CourseEnrollmentFinder courseEnrollmentFinder,
                                                        CourseEnrollmentNonpaymentWithdrawalPort courseEnrollmentService, ApplicationEventPublisher eventPublisher, Clock clock,
                                                        PlatformTransactionManager transactionManager) {
        this.standingRepository = standingRepository;
        this.courseEnrollmentFinder = courseEnrollmentFinder;
        this.courseEnrollmentService = courseEnrollmentService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public SuspendedEnrollmentClosureReport withdrawSuspendedEnrollmentsOfEndedCourses() {
        LocalDate today = LocalDate.now(clock);
        List<Long> candidateIds = courseEnrollmentFinder.findEndViewsByIds(standingRepository.findSuspendedCourseEnrollmentIds()).stream()
                .filter(view -> view.isActiveAndCourseEndedBefore(today)).map(CourseEnrollmentEndView::enrollmentId).sorted().toList();
        int closed = 0;
        List<SuspendedEnrollmentClosureReport.EnrollmentClosureFailure> failures = new ArrayList<>();
        for (Long id : candidateIds) {
            try {
                requiresNew.executeWithoutResult(status -> withdrawForNonpayment(id, today));
                closed++;
            } catch (RuntimeException e) {
                logFailure(id, e);
                failures.add(failureOf(id, e));
            }
        }
        return new SuspendedEnrollmentClosureReport(candidateIds.size(), closed, failures);
    }

    private void withdrawForNonpayment(Long enrollmentId, LocalDate today) {
        courseEnrollmentService.withdrawForNonpayment(enrollmentId);
        eventPublisher.publishEvent(EnrollmentClosedWhileSuspendedEvent.closedOn(EnrollmentKind.COURSE, enrollmentId, today));
        log.info("Inscripción de curso suspendida dada de baja al terminar el curso: inscripcion={}", enrollmentId);
    }

    private SuspendedEnrollmentClosureReport.EnrollmentClosureFailure failureOf(Long enrollmentId, RuntimeException failure) {
        if (failure instanceof ReportsPolicyViolations rejection) {
            String codes = rejection.violations().stream().map(PolicyViolation::code).collect(Collectors.joining(","));
            return new SuspendedEnrollmentClosureReport.EnrollmentClosureFailure(EnrollmentKind.COURSE, enrollmentId, true, "WITHDRAWAL_REJECTED_BY_RULE", codes);
        }
        return new SuspendedEnrollmentClosureReport.EnrollmentClosureFailure(EnrollmentKind.COURSE, enrollmentId, false, "SUBJECT_PROCESSING_FAILED", failure.getMessage());
    }

    private void logFailure(Long enrollmentId, RuntimeException failure) {
        if (failure instanceof ReportsPolicyViolations rejection) {
            log.warn("Baja por mora de curso rechazada por una regla: inscripcion={}, causa={}", enrollmentId,
                    rejection.violations().stream().map(PolicyViolation::code).toList());
            return;
        }
        log.error("Falló la baja por mora de la inscripción de curso: inscripcion={}", enrollmentId, failure);
    }
}
