package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.configuration.domain.CohortEntryVersion;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.discount.domain.CohortEntryRegistration;
import com.red_escuela.backend.billing.discount.domain.DiscountSavingCalculator;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountCandidateOrigin;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.operation.InvoiceDiscountCandidateStrategy;
import com.red_escuela.backend.billing.invoice.policy.issuing.CohortEntryDiscountAppliesPolicy;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class CohortEntryCandidateStrategy implements InvoiceDiscountCandidateStrategy {

    private static final Logger log = LoggerFactory.getLogger(CohortEntryCandidateStrategy.class);
    private static final int REASON_MAX_LENGTH = 500;
    private static final int FIRST_LINE_NUMBER = 1;

    private final BillingConfigurationReader configurationReader;
    private final StudentDiscountRepository studentDiscountRepository;
    private final Clock clock;
    private final CohortEntryDiscountAppliesPolicy appliesPolicy = new CohortEntryDiscountAppliesPolicy();

    public CohortEntryCandidateStrategy(BillingConfigurationReader configurationReader, StudentDiscountRepository studentDiscountRepository, Clock clock) {
        this.configurationReader = configurationReader;
        this.studentDiscountRepository = studentDiscountRepository;
        this.clock = clock;
    }

    @Override
    public DiscountCandidateOrigin origin() {
        return DiscountCandidateOrigin.COHORT_ENTRY;
    }

    @Override
    public List<InvoiceDiscountCandidate> findDiscountCandidatesForInvoice(InvoiceIssuingContext context) {
        return findEntryTerms(context.request())
                .map(version -> candidateOf(version, context))
                .filter(candidate -> !candidate.discountAmount().value().isZero())
                .stream().toList();
    }

    @Override
    public InvoiceDiscountCandidate registerWinnerBeforeInvoiceIsAssembled(InvoiceDiscountCandidate winner, InvoiceIssuingContext context) {
        InvoiceIssuingRequest request = context.request();
        Percentage discount = ((MonetaryAdjustment.Proportional) winner.calculation()).rate();
        String reason = reasonOf(discount);
        CohortEntryRegistration registration = new CohortEntryRegistration(EnrollmentReference.ofProgram(request.programEnrollmentId()), request.studentId(),
                context.facts().offeringCurrency(), winner.sourceVersionId(), discount, request.period(), request.actor(), reason);
        StudentDiscount saved = studentDiscountRepository.saveAndFlush(StudentDiscount.registerCohortEntryDiscount(registration, Instant.now(clock)));
        log.info("Descuento de ingreso de cohorte registrado: id={}, cohorte={}, inscripcion={}, version={}, porcentaje={}", saved.getId(), request.cohortId(),
                request.programEnrollmentId(), winner.sourceVersionId(), discount.points());
        return winner.withStudentDiscountId(saved.getId());
    }

    private Optional<CohortEntryVersion> findEntryTerms(InvoiceIssuingRequest request) {
        if (request.cohortId() == null || request.programEnrollmentId() == null) {
            return Optional.empty();
        }
        return configurationReader.findCohortEntryVersionEffectiveOn(request.cohortId(), request.pricingDate())
                .filter(version -> appliesPolicy.evaluateRule(new CohortEntryDiscountAppliesPolicy.Offer(request.entryEnrollmentFee(), request.issuingReason(),
                        version.terms().discount())).allowed());
    }

    private InvoiceDiscountCandidate candidateOf(CohortEntryVersion version, InvoiceIssuingContext context) {
        MonetaryAdjustment calculation = new MonetaryAdjustment.Proportional(version.terms().discount());
        NonNegativeMoney base = context.facts().chargeAmount();
        NonNegativeMoney saving = DiscountSavingCalculator.calculateSavingOnFinalPrice(calculation, base, Optional.empty());
        return new InvoiceDiscountCandidate(DiscountType.COHORT_ENTRY, context.request().cohortId(), null, "Matrícula de ingreso con descuento de " + pointsOf(version.terms().discount()) + "%",
                InvoiceType.ENROLLMENT_FEE, base, saving, null, null, FIRST_LINE_NUMBER, null, null, calculation, DiscountCandidateOrigin.COHORT_ENTRY, version.versionId());
    }

    private String reasonOf(Percentage discount) {
        String reason = "Descuento de ingreso de la cohorte del " + pointsOf(discount) + "% sobre la matrícula de ingreso";
        return reason.substring(0, Math.min(reason.length(), REASON_MAX_LENGTH));
    }

    private String pointsOf(Percentage discount) {
        return discount.points().stripTrailingZeros().toPlainString();
    }
}
