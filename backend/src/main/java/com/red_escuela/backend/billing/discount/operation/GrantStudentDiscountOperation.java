package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.LectiveYearSpan;
import com.red_escuela.backend.billing.discount.domain.ScholarshipPeriodRange;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrant;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantPreview;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantRequest;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.discount.exception.DiscountDefinitionNotFoundException;
import com.red_escuela.backend.billing.discount.exception.EnrollmentNotFoundForDiscountException;
import com.red_escuela.backend.billing.discount.policy.StudentDiscountGrantCheck;
import com.red_escuela.backend.billing.discount.policy.StudentDiscountGrantCheck.DefinitionFault;
import com.red_escuela.backend.billing.discount.policy.StudentDiscountGrantCheck.OverlappingDiscount;
import com.red_escuela.backend.billing.discount.policy.StudentDiscountRejections;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.discount.service.AnnualUpfrontCoverageReader;
import com.red_escuela.backend.billing.discount.service.EnrollmentDiscountLock;
import com.red_escuela.backend.billing.discount.service.EnrollmentFeeIssuanceReader;
import com.red_escuela.backend.billing.discount.service.EnrollmentLectiveYearReader;
import com.red_escuela.backend.billing.discount.service.NextMonthlyInstallmentPeriodQuery;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFactsFinder;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class GrantStudentDiscountOperation implements BillingOperation<GrantStudentDiscountCommand, StudentDiscount> {
    private static final Logger log = LoggerFactory.getLogger(GrantStudentDiscountOperation.class);
    private static final int INDIVIDUAL_DISCOUNT_PRIORITY = 1;

    private final PolicySet<StudentDiscountGrantCheck> grantPolicies;
    private final BillingEnrollmentFactsFinder enrollmentLookup;
    private final DiscountDefinitionRepository definitionRepository;
    private final StudentDiscountRepository repository;
    private final NextMonthlyInstallmentPeriodQuery nextInstallmentQuery;
    private final AnnualUpfrontCoverageReader annualUpfrontReader;
    private final EnrollmentLectiveYearReader lectiveYearReader;
    private final EnrollmentFeeIssuanceReader enrollmentFeeReader;
    private final EnrollmentDiscountLock enrollmentLock;
    private final Clock clock;

    public GrantStudentDiscountOperation(PolicySet<StudentDiscountGrantCheck> grantPolicies, BillingEnrollmentFactsFinder enrollmentLookup,
                                         DiscountDefinitionRepository definitionRepository, StudentDiscountRepository repository,
                                         NextMonthlyInstallmentPeriodQuery nextInstallmentQuery, AnnualUpfrontCoverageReader annualUpfrontReader, EnrollmentLectiveYearReader lectiveYearReader,
                                         EnrollmentFeeIssuanceReader enrollmentFeeReader, EnrollmentDiscountLock enrollmentLock, Clock clock) {
        this.grantPolicies = grantPolicies;
        this.enrollmentLookup = enrollmentLookup;
        this.definitionRepository = definitionRepository;
        this.repository = repository;
        this.nextInstallmentQuery = nextInstallmentQuery;
        this.annualUpfrontReader = annualUpfrontReader;
        this.lectiveYearReader = lectiveYearReader;
        this.enrollmentFeeReader = enrollmentFeeReader;
        this.enrollmentLock = enrollmentLock;
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
        enrollmentLock.lockDiscountsOf(command.enrollment());
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

    public StudentDiscountGrantPreview previewGrant(GrantStudentDiscountCommand command) {
        GrantAssessment assessment = assessGrant(command);
        StudentDiscountGrantCheck check = assessment.check();
        StudentDiscountGrantRequest request = assessment.request();
        int priority = assessment.resolution().definition().map(DiscountDefinition::getPriority).orElse(INDIVIDUAL_DISCOUNT_PRIORITY);
        return new StudentDiscountGrantPreview(assessment.result().violations(), request.discountType(), request.invoiceType(), request.calculation(), request.fromPeriod(),
                request.toPeriod(), check.nextInstallmentPeriod(), coveredPeriodsOf(request), priority, assessment.resolution().definition().map(DiscountDefinition::getId).orElse(null),
                check.annualUpfrontCoversRange(), check.enrollmentFeeAlreadyIssued(), check.overlappingDiscount() == null ? null : check.overlappingDiscount().id(),
                !command.requestedByDirector());
    }

    private int coveredPeriodsOf(StudentDiscountGrantRequest request) {
        if (request.fromPeriod() == null || request.toPeriod() == null || request.toPeriod().isBefore(request.fromPeriod())) {
            return 0;
        }
        return (int) ChronoUnit.MONTHS.between(request.fromPeriod(), request.toPeriod()) + 1;
    }

    private StudentDiscountGrant prepareGrant(GrantStudentDiscountCommand command) {
        GrantAssessment assessment = assessGrant(command);
        assessment.result().throwIfDenied(StudentDiscountRejections::rejectionOf);
        StudentDiscountGrantRequest request = assessment.request();
        Optional<DiscountDefinition> definition = assessment.resolution().definition();
        int priority = definition.map(DiscountDefinition::getPriority).orElse(INDIVIDUAL_DISCOUNT_PRIORITY);
        return new StudentDiscountGrant(command.enrollment(), assessment.facts().studentId(), assessment.facts().currency(), definition.map(DiscountDefinition::getId).orElse(null),
                request.discountType(), request.invoiceType(), request.calculation(), assessment.check().capPerInvoice(), priority,
                new ScholarshipPeriodRange(request.fromPeriod(), request.toPeriod()), request.reason().strip());
    }

    private GrantAssessment assessGrant(GrantStudentDiscountCommand command) {
        BillingEnrollmentFacts facts = findFactsOf(command);
        Optional<LectiveYearSpan> lectiveYear = lectiveYearReader.findLectiveYearCovering(command.enrollment(), facts, command.request().fromPeriod());
        StudentDiscountGrantRequest request = withDefaultToPeriod(command.request(), lectiveYear);
        DefinitionResolution resolution = resolveDefinition(request);
        Optional<DiscountDefinition> definition = resolution.definition();
        NonNegativeMoney cap = definition.map(DiscountDefinition::getCapPerInvoice).orElse(null);
        LocalDate today = LocalDate.now(clock);
        StudentDiscountGrantCheck check = new StudentDiscountGrantCheck(request.discountType(), request.invoiceType(), request.calculation(), cap, facts.currency(),
                request.fromPeriod(), request.toPeriod(), nextInstallmentQuery.findNextMonthlyInstallmentPeriod(command.enrollment(), today),
                annualUpfrontReader.annualUpfrontCoversAnyMonthOf(command.enrollment(), request.fromPeriod(), request.toPeriod()), request.reason(), resolution.fault(),
                command.requestedByDirector() ? null : command.authorizationExpiresOn(), today,
                findOverlappingDiscount(command.enrollment(), request, lectiveYear), enrollmentFeeIssued(command.enrollment(), request, lectiveYear));
        return new GrantAssessment(facts, request, resolution, check, grantPolicies.evaluateAllRules(check));
    }

    private record GrantAssessment(BillingEnrollmentFacts facts, StudentDiscountGrantRequest request, DefinitionResolution resolution, StudentDiscountGrantCheck check,
                                   PolicyEvaluationResult result) {
    }

    private StudentDiscountGrantRequest withDefaultToPeriod(StudentDiscountGrantRequest request, Optional<LectiveYearSpan> lectiveYear) {
        if (request.toPeriod() != null || request.fromPeriod() == null) {
            return request;
        }
        if (request.invoiceType() != InvoiceType.MONTHLY_INSTALLMENT) {
            return request.withToPeriod(request.fromPeriod());
        }
        return lectiveYear.map(span -> request.withToPeriod(span.end())).orElse(request);
    }

    private OverlappingDiscount findOverlappingDiscount(EnrollmentReference enrollment, StudentDiscountGrantRequest request, Optional<LectiveYearSpan> lectiveYear) {
        if (request.fromPeriod() == null || request.toPeriod() == null || request.toPeriod().isBefore(request.fromPeriod())) {
            return null;
        }
        List<StudentDiscount> active = enrollment.kind() == EnrollmentKind.PROGRAM
                ? repository.findByProgramEnrollmentIdAndStatus(enrollment.id(), StudentDiscountStatus.ACTIVE)
                : repository.findByCourseEnrollmentIdAndStatus(enrollment.id(), StudentDiscountStatus.ACTIVE);
        return active.stream()
                .filter(existing -> request.invoiceType() == InvoiceType.ENROLLMENT_FEE && lectiveYear.isPresent()
                        ? existing.coversLectiveYearOfSameKind(request.discountType(), request.invoiceType(), lectiveYear.get())
                        : existing.overlapsRangeOfSameKind(request.discountType(), request.invoiceType(), request.fromPeriod(), request.toPeriod()))
                .min(Comparator.comparing(StudentDiscount::getId))
                .map(existing -> new OverlappingDiscount(existing.getId(), existing.getFromPeriod(), existing.getToPeriod()))
                .orElse(null);
    }

    private boolean enrollmentFeeIssued(EnrollmentReference enrollment, StudentDiscountGrantRequest request, Optional<LectiveYearSpan> lectiveYear) {
        return request.invoiceType() == InvoiceType.ENROLLMENT_FEE && enrollmentFeeReader.enrollmentFeeIssuedIn(enrollment, lectiveYear);
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
