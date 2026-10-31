package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.discount.domain.CohortPromotionAcquisition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountSavingCalculator;
import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.ScholarshipPeriodRange;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountOrigin;
import com.red_escuela.backend.billing.discount.exception.CohortPromotionAlreadyAcquiredException;
import com.red_escuela.backend.billing.discount.exception.EnrollmentNotFoundForDiscountException;
import com.red_escuela.backend.billing.discount.policy.CohortPromotionAcquisitionCheck;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.discount.service.DiscountQuotaUsageReader;
import com.red_escuela.backend.billing.discount.service.EnrollmentDiscountLock;
import com.red_escuela.backend.billing.events.DiscountQuotaExhaustedEvent;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountCandidateOrigin;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountCandidateRanking;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.pricing.domain.PriceVersionResolution;
import com.red_escuela.backend.billing.pricing.service.PriceReader;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFactsFinder;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Component
public class AcquireCohortPromotionOperation implements BillingOperation<AcquireCohortPromotionCommand, Optional<AcquiredCohortPromotion>> {
    private static final Logger log = LoggerFactory.getLogger(AcquireCohortPromotionOperation.class);
    private static final int REASON_MAX_LENGTH = 500;
    private static final String REASON_PREFIX = "Promoción de cohorte adquirida al inscribirse: ";
    private static final DiscountCandidateRanking RANKING = new DiscountCandidateRanking();

    private final PolicySet<CohortPromotionAcquisitionCheck> acquisitionPolicies;
    private final BillingEnrollmentFactsFinder enrollmentLookup;
    private final DiscountDefinitionRepository definitionRepository;
    private final StudentDiscountRepository repository;
    private final DiscountQuotaUsageReader quotaUsageReader;
    private final EnrollmentDiscountLock enrollmentLock;
    private final StudentCurrentAccountService accountService;
    private final PriceReader priceReader;
    private final Clock clock;

    public AcquireCohortPromotionOperation(PolicySet<CohortPromotionAcquisitionCheck> acquisitionPolicies, BillingEnrollmentFactsFinder enrollmentLookup,
                                           DiscountDefinitionRepository definitionRepository, StudentDiscountRepository repository,
                                           DiscountQuotaUsageReader quotaUsageReader, EnrollmentDiscountLock enrollmentLock, StudentCurrentAccountService accountService, PriceReader priceReader, Clock clock) {
        this.acquisitionPolicies = acquisitionPolicies;
        this.enrollmentLookup = enrollmentLookup;
        this.definitionRepository = definitionRepository;
        this.repository = repository;
        this.quotaUsageReader = quotaUsageReader;
        this.enrollmentLock = enrollmentLock;
        this.accountService = accountService;
        this.priceReader = priceReader;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.ACQUIRE_COHORT_PROMOTION;
    }

    @Override
    public Class<AcquireCohortPromotionCommand> contextType() {
        return AcquireCohortPromotionCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(acquisitionPolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(DiscountQuotaExhaustedEvent.class);
    }

    @Override
    public void requireValidContext(AcquireCohortPromotionCommand command) {
        if (!findCandidateIds(command).isEmpty()) {
            requireNoCohortPromotionYet(command.enrollment());
        }
    }

    @Override
    public Optional<AcquiredCohortPromotion> processOperation(AcquireCohortPromotionCommand command) {
        List<Long> candidateIds = findCandidateIds(command);
        if (candidateIds.isEmpty()) {
            return Optional.empty();
        }
        BillingEnrollmentFacts facts = findFactsOf(command);
        enrollmentLock.lockDiscountsOf(command.enrollment());
        accountService.findOrOpenAndLockStudentCurrentAccount(facts.studentId(), facts.currency());
        List<DiscountDefinition> lockedDefinitions = candidateIds.stream()
                .map(id -> definitionRepository.findByIdForUpdate(id).orElseThrow())
                .filter(definition -> definition.isAvailableForAcquisitionOn(command.enrollmentDate()))
                .toList();
        if (lockedDefinitions.isEmpty()) {
            return Optional.empty();
        }
        requireNoCohortPromotionYet(command.enrollment());
        Instant now = Instant.now(clock);
        for (DiscountDefinition definition : rankMostFavorableFirst(lockedDefinitions, command, facts)) {
            long placesUsed = quotaUsageReader.countPlacesUsed(definition.getId());
            if (definition.hasPlaceLeft(placesUsed)) {
                return Optional.of(acquire(definition, placesUsed, command, facts, now));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(AcquireCohortPromotionCommand command, Optional<AcquiredCohortPromotion> result) {
        return result.filter(AcquiredCohortPromotion::tookLastPlace)
                .map(acquired -> DiscountQuotaExhaustedEvent.exhaustedFor(acquired.definition().getId(), acquired.definition().getName(), acquired.definition().getQuota()))
                .stream().toList();
    }

    private AcquiredCohortPromotion acquire(DiscountDefinition definition, long placesUsed, AcquireCohortPromotionCommand command, BillingEnrollmentFacts facts, Instant now) {
        boolean tookLastPlace = definition.takesLastPlaceAfter(placesUsed);
        String reason = REASON_PREFIX + definition.getName();
        CohortPromotionAcquisition acquisition = new CohortPromotionAcquisition(command.enrollment(), facts.studentId(), facts.currency(), definition,
                referenceRangeOf(definition, command.enrollmentDate()), command.actorPersonId(), reason.substring(0, Math.min(reason.length(), REASON_MAX_LENGTH)));
        StudentDiscount saved = repository.saveAndFlush(StudentDiscount.acquireCohortPromotion(acquisition, now));
        if (tookLastPlace) {
            definition.markQuotaExhaustionNotified(now);
            definitionRepository.saveAndFlush(definition);
        }
        log.info("Promoción de cohorte adquirida: id={}, definicion={}, inscripcion={}:{}, cupoUsadoAntes={}, cupo={}, ultimoLugar={}", saved.getId(), definition.getId(),
                command.enrollment().kind(), command.enrollment().id(), placesUsed, definition.getQuota(), tookLastPlace);
        return new AcquiredCohortPromotion(saved, definition, tookLastPlace);
    }

    private List<DiscountDefinition> rankMostFavorableFirst(List<DiscountDefinition> definitions, AcquireCohortPromotionCommand command, BillingEnrollmentFacts facts) {
        return definitions.stream()
                .map(definition -> new RankedDefinition(definition, candidateOf(definition, command.enrollmentDate(), facts)))
                .sorted((first, second) -> RANKING.compare(first.candidate(), second.candidate()))
                .map(RankedDefinition::definition)
                .toList();
    }

    private InvoiceDiscountCandidate candidateOf(DiscountDefinition definition, LocalDate date, BillingEnrollmentFacts facts) {
        NonNegativeMoney base = findFinalPriceOn(definition, date).orElse(NonNegativeMoney.zero(facts.currency()));
        NonNegativeMoney saving = DiscountSavingCalculator.calculateSavingOnFinalPrice(definition.getCalculation(), base, Optional.ofNullable(definition.getCapPerInvoice()));
        return new InvoiceDiscountCandidate(DiscountType.COHORT_PROMOTION, definition.getId(), null, definition.getName(), definition.getApplicableInvoiceType(), base, saving,
                null, definition.getInstallmentsTotal(), 1, definition.getPriority(), definition.getCreatedAt(), definition.getCalculation(), DiscountCandidateOrigin.STUDENT_DISCOUNT);
    }

    private Optional<NonNegativeMoney> findFinalPriceOn(DiscountDefinition definition, LocalDate date) {
        return definition.findPricedOffering().flatMap(offering -> definition.findPricedFeeType()
                        .flatMap(feeType -> priceReader.findPriceVersionEffectiveOn(offering, feeType, date)))
                .map(PriceVersionResolution::finalAmount);
    }

    private ScholarshipPeriodRange referenceRangeOf(DiscountDefinition definition, LocalDate enrollmentDate) {
        YearMonth from = YearMonth.from(enrollmentDate);
        if (definition.getAvailableUntil() != null) {
            YearMonth until = YearMonth.from(definition.getAvailableUntil());
            return new ScholarshipPeriodRange(from, until.isBefore(from) ? from : until);
        }
        Integer installments = definition.getInstallmentsTotal();
        return new ScholarshipPeriodRange(from, installments == null ? from : from.plusMonths(installments - 1L));
    }

    private List<Long> findCandidateIds(AcquireCohortPromotionCommand command) {
        return command.offering().belongsToProgram()
                ? definitionRepository.findActiveCohortPromotionIdsOfProgram(command.offering().programId(), command.programCohortId())
                : definitionRepository.findActiveCohortPromotionIdsOfCourse(command.offering().courseId());
    }

    private void requireNoCohortPromotionYet(EnrollmentReference enrollment) {
        Optional<StudentDiscount> existing = enrollment.kind() == EnrollmentKind.PROGRAM
                ? repository.findFirstByProgramEnrollmentIdAndOriginOrderByIdAsc(enrollment.id(), StudentDiscountOrigin.COHORT_ACQUISITION)
                : repository.findFirstByCourseEnrollmentIdAndOriginOrderByIdAsc(enrollment.id(), StudentDiscountOrigin.COHORT_ACQUISITION);
        acquisitionPolicies.evaluateAllRules(new CohortPromotionAcquisitionCheck(existing.map(StudentDiscount::getId).orElse(null)))
                .throwIfDenied(CohortPromotionAlreadyAcquiredException::new);
    }

    private BillingEnrollmentFacts findFactsOf(AcquireCohortPromotionCommand command) {
        Optional<BillingEnrollmentFacts> facts = command.enrollment().kind() == EnrollmentKind.PROGRAM
                ? enrollmentLookup.findProgramEnrollmentFacts(command.enrollment().id())
                : enrollmentLookup.findCourseEnrollmentFacts(command.enrollment().id());
        return facts.orElseThrow(() -> new EnrollmentNotFoundForDiscountException(command.enrollment().kind().name(), command.enrollment().id()));
    }

    private record RankedDefinition(DiscountDefinition definition, InvoiceDiscountCandidate candidate) {
    }
}
