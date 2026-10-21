package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class StudentDiscountEnrollmentEndService {
    private static final Logger log = LoggerFactory.getLogger(StudentDiscountEnrollmentEndService.class);
    private static final String ENROLLMENT_END_PROCESS = "ENROLLMENT_ENDED";
    private static final Set<StudentDiscountStatus> ENDABLE_STATUSES = Set.of(StudentDiscountStatus.ACTIVE);

    private final StudentDiscountRepository repository;
    private final EnrollmentDiscountLock enrollmentLock;
    private final Clock clock;

    public StudentDiscountEnrollmentEndService(StudentDiscountRepository repository, EnrollmentDiscountLock enrollmentLock, Clock clock) {
        this.repository = repository;
        this.enrollmentLock = enrollmentLock;
        this.clock = clock;
    }

    @Transactional
    public void endActiveDiscountsOf(WithdrawnEnrollmentFacts facts) {
        EnrollmentReference enrollment = facts.programEnrollment().map(EnrollmentReference::ofProgram).orElseGet(() -> EnrollmentReference.ofCourse(facts.courseEnrollmentId()));
        enrollmentLock.lockDiscountsOf(enrollment);
        List<StudentDiscount> active = enrollment.kind() == EnrollmentKind.PROGRAM
                ? repository.findByProgramEnrollmentIdAndStatusInForUpdate(enrollment.id(), ENDABLE_STATUSES)
                : repository.findByCourseEnrollmentIdAndStatusInForUpdate(enrollment.id(), ENDABLE_STATUSES);
        StatusChangeActor actor = StatusChangeActor.ofProcess(ENROLLMENT_END_PROCESS);
        Instant now = Instant.now(clock);
        active.forEach(discount -> discount.endWithEnrollment(actor, now));
        repository.saveAll(active);
        if (!active.isEmpty()) {
            log.info("Descuentos finalizados por el fin de la inscripción: inscripcion={}:{}, causa={}, cantidad={}", enrollment.kind(), enrollment.id(), facts.cause(), active.size());
        }
    }
}
