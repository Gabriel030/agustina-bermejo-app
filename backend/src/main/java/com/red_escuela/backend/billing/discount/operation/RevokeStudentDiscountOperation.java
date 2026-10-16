package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountEvent;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStateMachine;
import com.red_escuela.backend.billing.discount.exception.StudentDiscountNotFoundException;
import com.red_escuela.backend.billing.discount.policy.StudentDiscountRejections;
import com.red_escuela.backend.billing.discount.policy.StudentDiscountRevocationCheck;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.discount.service.DiscountActorResolver;
import com.red_escuela.backend.billing.discount.service.NextMonthlyInstallmentPeriodQuery;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Component
public class RevokeStudentDiscountOperation implements BillingOperation<RevokeStudentDiscountCommand, StudentDiscount> {
    private static final Logger log = LoggerFactory.getLogger(RevokeStudentDiscountOperation.class);

    private final PolicySet<StudentDiscountRevocationCheck> revocationPolicies;
    private final StudentDiscountRepository repository;
    private final NextMonthlyInstallmentPeriodQuery nextInstallmentQuery;
    private final DiscountActorResolver actorResolver;
    private final Clock clock;

    public RevokeStudentDiscountOperation(PolicySet<StudentDiscountRevocationCheck> revocationPolicies, StudentDiscountRepository repository,
                                          NextMonthlyInstallmentPeriodQuery nextInstallmentQuery, DiscountActorResolver actorResolver, Clock clock) {
        this.revocationPolicies = revocationPolicies;
        this.repository = repository;
        this.nextInstallmentQuery = nextInstallmentQuery;
        this.actorResolver = actorResolver;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.REVOKE_STUDENT_DISCOUNT;
    }

    @Override
    public Class<RevokeStudentDiscountCommand> contextType() {
        return RevokeStudentDiscountCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(revocationPolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of();
    }

    @Override
    public void requireValidContext(RevokeStudentDiscountCommand command) {
        revocationPolicies.evaluateAllRules(new StudentDiscountRevocationCheck(command.reason(),
                command.requestedByDirector() ? null : command.authorizationExpiresOn(), LocalDate.now(clock))).throwIfDenied(StudentDiscountRejections::rejectionOf);
        StudentDiscountStateMachine.requireTargetFor(repository.findStatusById(command.studentDiscountId())
                .orElseThrow(() -> new StudentDiscountNotFoundException(command.studentDiscountId())), StudentDiscountEvent.DISCOUNT_REVOKED);
    }

    @Override
    public StudentDiscount processOperation(RevokeStudentDiscountCommand command) {
        StudentDiscount discount = repository.findByIdForUpdate(command.studentDiscountId())
                .orElseThrow(() -> new StudentDiscountNotFoundException(command.studentDiscountId()));
        StatusChangeActor actor = StatusChangeActor.ofPerson(actorResolver.currentPersonId());
        YearMonth revokedFrom = nextInstallmentQuery.findNextMonthlyInstallmentPeriod(enrollmentOf(discount), LocalDate.now(clock));
        discount.revokeStudentDiscount(command.reason().strip(), revokedFrom, actor, Instant.now(clock));
        StudentDiscount saved = repository.saveAndFlush(discount);
        log.info("Descuento revocado: id={}, tipo={}, desdeLaCuota={}, actor={}, solicitante={}", saved.getId(), saved.getDiscountType(), revokedFrom, actor.personId(),
                command.requestedByPersonId());
        return saved;
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(RevokeStudentDiscountCommand command, StudentDiscount discount) {
        return List.of();
    }

    private EnrollmentReference enrollmentOf(StudentDiscount discount) {
        return discount.getProgramEnrollmentId() != null
                ? new EnrollmentReference(EnrollmentKind.PROGRAM, discount.getProgramEnrollmentId())
                : new EnrollmentReference(EnrollmentKind.COURSE, discount.getCourseEnrollmentId());
    }
}
