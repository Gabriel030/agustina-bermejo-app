package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.ScholarshipPeriodRange;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrant;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantRequest;
import com.red_escuela.backend.billing.discount.exception.DiscountDefinitionNotFoundException;
import com.red_escuela.backend.billing.discount.exception.EnrollmentNotFoundForDiscountException;
import com.red_escuela.backend.billing.discount.policy.StudentDiscountGrantCheck;
import com.red_escuela.backend.billing.discount.policy.StudentDiscountGrantCheck.DefinitionFault;
import com.red_escuela.backend.billing.discount.policy.StudentDiscountRejections;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.discount.service.AnnualUpfrontCoverageReader;
import com.red_escuela.backend.billing.discount.service.NextMonthlyInstallmentPeriodQuery;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentLookup;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class GrantStudentDiscountOperation implements BillingOperation<GrantStudentDiscountCommand, StudentDiscount> {
    private static final Logger log = LoggerFactory.getLogger(GrantStudentDiscountOperation.class);
    private static final int INDIVIDUAL_DISCOUNT_PRIORITY = 1;

    private final PolicySet<StudentDiscountGrantCheck> grantPolicies;
    private final BillingEnrollmentLookup enrollmentLookup;
    private final DiscountDefinitionRepository definitionRepository;
    private final StudentDiscountRepository repository;
    private final NextMonthlyInstallmentPeriodQuery nextInstallmentQuery;
    private final AnnualUpfrontCoverageReader annualUpfrontReader;
    private final Clock clock;

    public GrantStudentDiscountOperation(PolicySet<StudentDiscountGrantCheck> grantPolicies, BillingEnrollmentLookup enrollmentLookup,
                                         DiscountDefinitionRepository definitionRepository, StudentDiscountRepository repository,
                                         NextMonthlyInstallmentPeriodQuery nextInstallmentQuery, AnnualUpfrontCoverageReader annualUpfrontReader, Clock clock) {
        this.grantPolicies = grantPolicies;
        this.enrollmentLookup = enrollmentLookup;
        this.definitionRepository = definitionRepository;
        this.repository = repository;
        this.nextInstallmentQuery = nextInstallmentQuery;
        this.annualUpfrontReader = annualUpfrontReader;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.GRANT_STUDENT_DISCOUNT;
    }

    @Override
    public Class<GrantStudentDiscountCommand> contextType() {
        return GrantStudentDiscountCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(grantPolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of();
    }

    @Override
    public void requireValidContext(GrantStudentDiscountCommand command) {
        prepareGrant(command);
    }

    @Override
    public StudentDiscount processOperation(GrantStudentDiscountCommand command) {
        StudentDiscountGrant grant = prepareGrant(command);
        StudentDiscount saved = repository.saveAndFlush(
                StudentDiscount.grantStudentDiscount(grant, command.requestedByPersonId(), command.authorizationRequestId(), Instant.now(clock)));
        log.info("Descuento otorgado: id={}, tipo={}, inscripcion={}:{}, tipoDeFactura={}, calculo={}, desde={}, hasta={}, actor={}, publicacionDirecta={}, authorizationRequestId={}",
                saved.getId(), saved.getDiscountType(), grant.enrollment().kind(), grant.enrollment().id(), saved.getApplicableInvoiceType(), saved.getCalculation(),
                saved.getFromPeriod(), saved.getToPeriod(), saved.getGrantedById(), command.requestedByDirector(), saved.getAuthorizationRequestId());
        return saved;
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(GrantStudentDiscountCommand command, StudentDiscount discount) {
        return List.of();
    }

    private StudentDiscountGrant prepareGrant(GrantStudentDiscountCommand command) {
        StudentDiscountGrantRequest request = command.request();
        BillingEnrollmentFacts facts = findFactsOf(command);
        DefinitionResolution resolution = resolveDefinition(request);
        Optional<DiscountDefinition> definition = resolution.definition();
        NonNegativeMoney cap = definition.map(DiscountDefinition::getCapPerInvoice).orElse(null);
        LocalDate today = LocalDate.now(clock);
        StudentDiscountGrantCheck check = new StudentDiscountGrantCheck(request.discountType(), request.invoiceType(), request.calculation(), cap, facts.currency(),
                request.fromPeriod(), request.toPeriod(), nextInstallmentQuery.findNextMonthlyInstallmentPeriod(command.enrollment(), today),
                annualUpfrontReader.annualUpfrontCoversAnyMonthOf(command.enrollment(), request.fromPeriod(), request.toPeriod()), request.reason(), resolution.fault());
        grantPolicies.evaluateAllRules(check).throwIfDenied(StudentDiscountRejections::rejectionOf);
        int priority = definition.map(DiscountDefinition::getPriority).orElse(INDIVIDUAL_DISCOUNT_PRIORITY);
        return new StudentDiscountGrant(command.enrollment(), facts.studentId(), facts.currency(), definition.map(DiscountDefinition::getId).orElse(null),
                request.discountType(), request.invoiceType(), request.calculation(), cap, priority,
                new ScholarshipPeriodRange(request.fromPeriod(), request.toPeriod()), request.reason().strip());
    }

    private BillingEnrollmentFacts findFactsOf(GrantStudentDiscountCommand command) {
        Optional<BillingEnrollmentFacts> facts = command.enrollment().kind() == EnrollmentKind.PROGRAM
                ? enrollmentLookup.findProgramEnrollmentFacts(command.enrollment().id())
                : enrollmentLookup.findCourseEnrollmentFacts(command.enrollment().id());
        return facts.orElseThrow(() -> new EnrollmentNotFoundForDiscountException(command.enrollment().kind().name(), command.enrollment().id()));
    }

    private DefinitionResolution resolveDefinition(StudentDiscountGrantRequest request) {
        if (request.discountType() == DiscountType.INDIVIDUAL) {
            return new DefinitionResolution(Optional.empty(), request.discountDefinitionId() == null ? DefinitionFault.NONE : DefinitionFault.UNEXPECTED_FOR_INDIVIDUAL);
        }
        Optional<DiscountDefinition> definition = request.discountDefinitionId() == null
                ? definitionRepository.findFirstByDiscountTypeAndStatusOrderByIdAsc(DiscountDefinitionType.SCHOLARSHIP, DiscountDefinitionStatus.ACTIVE)
                : Optional.of(definitionRepository.findById(request.discountDefinitionId()).orElseThrow(() -> new DiscountDefinitionNotFoundException(request.discountDefinitionId())));
        if (definition.isEmpty()) {
            return new DefinitionResolution(definition, DefinitionFault.MISSING);
        }
        if (definition.get().getDiscountType() != DiscountDefinitionType.SCHOLARSHIP) {
            return new DefinitionResolution(definition, DefinitionFault.NOT_A_SCHOLARSHIP);
        }
        return new DefinitionResolution(definition, definition.get().getStatus() == DiscountDefinitionStatus.ACTIVE ? DefinitionFault.NONE : DefinitionFault.CLOSED);
    }

    private record DefinitionResolution(Optional<DiscountDefinition> definition, DefinitionFault fault) {
    }
}
