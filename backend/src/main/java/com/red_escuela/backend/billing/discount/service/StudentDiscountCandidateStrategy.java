package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.AppliedStudentDiscountInstallment;
import com.red_escuela.backend.billing.discount.domain.CohortPromotionProgress;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountSavingCalculator;
import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.LectiveYearSpan;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountOrigin;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.discount.repository.AppliedStudentDiscountRepository;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountCandidateOrigin;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.operation.InvoiceDiscountCandidateStrategy;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFactsFinder;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class StudentDiscountCandidateStrategy implements InvoiceDiscountCandidateStrategy {

    private static final int MAX_NAME_LENGTH = 200;
    private static final String PROCESS_NAME = "INVOICE_ISSUING";

    private final EnrollmentStudentDiscountReader discountReader;
    private final EnrollmentLectiveYearReader lectiveYearReader;
    private final BillingEnrollmentFactsFinder enrollmentLookup;
    private final EnrollmentFeeIssuanceReader enrollmentFeeReader;
    private final AppliedStudentDiscountRepository appliedRepository;
    private final DiscountDefinitionRepository definitionRepository;
    private final StudentDiscountRepository studentDiscountRepository;
    private final Clock clock;

    public StudentDiscountCandidateStrategy(EnrollmentStudentDiscountReader discountReader, EnrollmentLectiveYearReader lectiveYearReader,
                                            BillingEnrollmentFactsFinder enrollmentLookup, EnrollmentFeeIssuanceReader enrollmentFeeReader,
                                            AppliedStudentDiscountRepository appliedRepository, DiscountDefinitionRepository definitionRepository,
                                            StudentDiscountRepository studentDiscountRepository, Clock clock) {
        this.appliedRepository = appliedRepository;
        this.definitionRepository = definitionRepository;
        this.studentDiscountRepository = studentDiscountRepository;
        this.clock = clock;
        this.discountReader = discountReader;
        this.lectiveYearReader = lectiveYearReader;
        this.enrollmentLookup = enrollmentLookup;
        this.enrollmentFeeReader = enrollmentFeeReader;
    }

    @Override
    public DiscountCandidateOrigin origin() {
        return DiscountCandidateOrigin.STUDENT_DISCOUNT;
    }

    @Override
    public List<InvoiceDiscountCandidate> findDiscountCandidatesForInvoice(InvoiceIssuingContext context) {
        return findCandidates(context, discountReader::readApplicableDiscountsAsDetachedSnapshot);
    }

    @Override
    public List<InvoiceDiscountCandidate> findDiscountCandidatesUnderEnrollmentLock(InvoiceIssuingContext context) {
        return findCandidates(context, discountReader::lockEnrollmentAndReadApplicableDiscounts);
    }

    private List<InvoiceDiscountCandidate> findCandidates(InvoiceIssuingContext context, Function<EnrollmentReference, List<StudentDiscount>> reader) {
        InvoiceIssuingRequest request = context.request();
        InvoiceType invoiceType = request.invoiceType();
        if (invoiceType == InvoiceType.LATE_FEE) {
            return List.of();
        }
        Optional<EnrollmentReference> enrollment = enrollmentOf(request);
        if (enrollment.isEmpty()) {
            return List.of();
        }
        List<StudentDiscount> sameKind = reader.apply(enrollment.get()).stream()
                .filter(discount -> discount.getApplicableInvoiceType() == invoiceType && discount.getCurrency() == context.facts().offeringCurrency())
                .toList();
        List<StudentDiscount> discounts = sameKind.stream().filter(discount -> discount.getOrigin() == StudentDiscountOrigin.DIRECT_GRANT).toList();
        List<InvoiceDiscountCandidate> cohortCandidates = findCohortPromotionCandidates(sameKind.stream()
                .filter(discount -> discount.getOrigin() == StudentDiscountOrigin.COHORT_ACQUISITION).toList(), context);
        if (discounts.isEmpty()) {
            return cohortCandidates;
        }
        Optional<LectiveYearSpan> lectiveYear = invoiceType == InvoiceType.ENROLLMENT_FEE ? findLectiveYearOfEnrollmentFee(enrollment.get(), request) : Optional.empty();
        List<InvoiceDiscountCandidate> directCandidates = discounts.stream()
                .filter(discount -> invoiceType == InvoiceType.ENROLLMENT_FEE
                        ? isFirstLiveEnrollmentFeeOfLectiveYear(discount, enrollment.get(), lectiveYear) : discount.coversPeriod(request.period()))
                .map(discount -> candidateOf(discount, context))
                .filter(candidate -> !candidate.discountAmount().value().isZero())
                .toList();
        return Stream.concat(directCandidates.stream(), cohortCandidates.stream()).toList();
    }

    @Override
    public void markDiscountAsApplied(InvoiceDiscountCandidate winner) {
        if (winner.discountType() != DiscountType.COHORT_PROMOTION || winner.installmentsTotal() == null) {
            return;
        }
        StudentDiscount discount = studentDiscountRepository.findById(winner.studentDiscountId()).orElseThrow();
        boolean allInstallmentsLive = appliedRepository.findLiveApplicationsOf(List.of(discount.getId())).size() >= winner.installmentsTotal();
        if (allInstallmentsLive && discount.getStatus() == StudentDiscountStatus.ACTIVE) {
            discount.recordLastInstallmentConsumed(StatusChangeActor.ofProcess(PROCESS_NAME), Instant.now(clock));
            studentDiscountRepository.save(discount);
        }
    }

    private List<InvoiceDiscountCandidate> findCohortPromotionCandidates(List<StudentDiscount> promotions, InvoiceIssuingContext context) {
        List<StudentDiscount> active = promotions.stream()
                .filter(promotion -> promotion.getStatus() == StudentDiscountStatus.ACTIVE || promotion.getStatus() == StudentDiscountStatus.EXHAUSTED).toList();
        if (active.isEmpty()) {
            return List.of();
        }
        Map<Long, DiscountDefinition> definitions = definitionRepository.findAllById(active.stream().map(StudentDiscount::getDiscountDefinitionId).toList()).stream()
                .collect(Collectors.toMap(DiscountDefinition::getId, Function.identity()));
        Map<Long, List<AppliedStudentDiscountInstallment>> usesById = appliedRepository.findLiveApplicationsOf(active.stream().map(StudentDiscount::getId).toList()).stream()
                .collect(Collectors.groupingBy(AppliedStudentDiscountInstallment::studentDiscountId));
        return active.stream()
                .map(promotion -> new CohortPromotionProgress(promotion, definitions.get(promotion.getDiscountDefinitionId()).getAvailableUntil(), usesById.getOrDefault(promotion.getId(), List.of())))
                .map(progress -> withReissuedInvoiceOriginalIssueDate(progress, context))
                .filter(progress -> progress.canBeAppliedOn(context.facts().issueDate()))
                .map(progress -> cohortCandidateOf(progress, context))
                .filter(candidate -> !candidate.discountAmount().value().isZero())
                .toList();
    }

    private CohortPromotionProgress withReissuedInvoiceOriginalIssueDate(CohortPromotionProgress progress, InvoiceIssuingContext context) {
        if (!progress.isWindowClosedOn(context.facts().issueDate())) {
            return progress;
        }
        return context.request().findChargeKey()
                .flatMap(chargeKey -> appliedRepository.findEarliestIssueDateOfChargeCancelledByBillingError(progress.promotion().getId(), chargeKey.value()))
                .map(progress::withReissuedInvoiceOriginalIssueDate)
                .orElse(progress);
    }

    private InvoiceDiscountCandidate cohortCandidateOf(CohortPromotionProgress progress, InvoiceIssuingContext context) {
        StudentDiscount promotion = progress.promotion();
        NonNegativeMoney base = context.facts().chargeAmount();
        NonNegativeMoney saving = DiscountSavingCalculator.calculateSavingOnFinalPrice(promotion.getCalculation(), base, Optional.ofNullable(promotion.getCapPerInvoice()));
        String name = "Promoción: " + promotion.getReason();
        return new InvoiceDiscountCandidate(promotion.getDiscountType(), promotion.getId(), promotion.getId(), name.substring(0, Math.min(name.length(), MAX_NAME_LENGTH)),
                promotion.getApplicableInvoiceType(), base, saving, progress.nextInstallmentNumber().isPresent() ? progress.nextInstallmentNumber().getAsInt() : null,
                promotion.getInstallmentsTotal(), 1, promotion.getPriority(), promotion.getGrantedAt(), promotion.getCalculation(), DiscountCandidateOrigin.STUDENT_DISCOUNT);
    }

    private Optional<EnrollmentReference> enrollmentOf(InvoiceIssuingRequest request) {
        if (request.programEnrollmentId() != null) {
            return Optional.of(EnrollmentReference.ofProgram(request.programEnrollmentId()));
        }
        return request.courseEnrollmentId() == null ? Optional.empty() : Optional.of(EnrollmentReference.ofCourse(request.courseEnrollmentId()));
    }

    private Optional<LectiveYearSpan> findLectiveYearOfEnrollmentFee(EnrollmentReference enrollment, InvoiceIssuingRequest request) {
        Optional<BillingEnrollmentFacts> facts = enrollment.kind() == EnrollmentKind.PROGRAM
                ? enrollmentLookup.findProgramEnrollmentFacts(enrollment.id())
                : enrollmentLookup.findCourseEnrollmentFacts(enrollment.id());
        return facts.flatMap(enrollmentFacts -> lectiveYearReader.findLectiveYearCovering(enrollment, enrollmentFacts, request.period()));
    }

    private boolean isFirstLiveEnrollmentFeeOfLectiveYear(StudentDiscount discount, EnrollmentReference enrollment, Optional<LectiveYearSpan> lectiveYear) {
        return discount.getStatus() == StudentDiscountStatus.ACTIVE
                && lectiveYear.map(span -> span.contains(discount.getFromPeriod())).orElse(false)
                && !enrollmentFeeReader.enrollmentFeeIssuedIn(enrollment, lectiveYear);
    }

    private InvoiceDiscountCandidate candidateOf(StudentDiscount discount, InvoiceIssuingContext context) {
        InvoiceIssuingRequest request = context.request();
        NonNegativeMoney base = context.facts().chargeAmount();
        NonNegativeMoney saving = DiscountSavingCalculator.calculateSavingOnFinalPrice(discount.getCalculation(), base, Optional.ofNullable(discount.getCapPerInvoice()));
        boolean monthly = discount.getApplicableInvoiceType() == InvoiceType.MONTHLY_INSTALLMENT;
        Integer installmentNumber = monthly ? discount.periodRange().ordinalOf(request.period()) : null;
        Integer installmentsTotal = monthly ? discount.periodRange().monthCount() : null;
        return new InvoiceDiscountCandidate(discount.getDiscountType(), discount.getId(), discount.getId(), nameOf(discount), discount.getApplicableInvoiceType(), base, saving,
                installmentNumber, installmentsTotal, 1, discount.getPriority(), discount.getGrantedAt(), discount.getCalculation(),
                DiscountCandidateOrigin.STUDENT_DISCOUNT);
    }

    private String nameOf(StudentDiscount discount) {
        String prefix = discount.getDiscountType() == DiscountType.SCHOLARSHIP ? "Beca: " : "Descuento individual: ";
        String name = prefix + discount.getReason();
        return name.substring(0, Math.min(name.length(), MAX_NAME_LENGTH));
    }
}
