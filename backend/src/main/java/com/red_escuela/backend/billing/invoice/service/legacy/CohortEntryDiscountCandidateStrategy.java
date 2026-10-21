package com.red_escuela.backend.billing.invoice.service.legacy;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountCandidateOrigin;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.operation.InvoiceDiscountCandidateStrategy;
import com.red_escuela.backend.billing.invoice.policy.issuing.CohortEntryDiscountAppliesPolicy;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CohortEntryDiscountCandidateStrategy implements InvoiceDiscountCandidateStrategy {

    private final ProgramCohortRepository cohortRepository;
    private final BillingConfigurationReader configurationReader;
    private final CohortEntryDiscountAppliesPolicy appliesPolicy = new CohortEntryDiscountAppliesPolicy();

    public CohortEntryDiscountCandidateStrategy(ProgramCohortRepository cohortRepository, BillingConfigurationReader configurationReader) {
        this.cohortRepository = cohortRepository;
        this.configurationReader = configurationReader;
    }

    @Override
    public DiscountCandidateOrigin origin() {
        return DiscountCandidateOrigin.LEGACY_COHORT_ENTRY;
    }

    @Override
    public List<InvoiceDiscountCandidate> findDiscountCandidatesForInvoice(InvoiceIssuingContext context) {
        InvoiceIssuingRequest request = context.request();
        if (request.cohortId() == null) {
            return List.of();
        }
        ProgramCohort cohort = cohortRepository.findById(request.cohortId()).orElseThrow();
        Percentage discount = configurationReader.cohortEntryDiscountEffectiveOn(cohort.getId(), request.pricingDate());
        if (!appliesPolicy.evaluateRule(new CohortEntryDiscountAppliesPolicy.Offer(request.entryEnrollmentFee(), request.issuingReason(), discount)).allowed()) {
            return List.of();
        }
        NonNegativeMoney base = context.facts().chargeAmount();
        NonNegativeMoney amount = DiscountAmounts.discountOf(new MonetaryAdjustment.Proportional(discount), base);
        return amount.value().isZero() ? List.of() : List.of(InvoiceDiscountCandidate.ofLegacySource(DiscountCandidateOrigin.LEGACY_COHORT_ENTRY, DiscountType.COHORT_ENTRY, cohort.getId(), null,
                "Matrícula de ingreso con descuento de " + discount.points().stripTrailingZeros().toPlainString() + "%", InvoiceType.ENROLLMENT_FEE, base, amount, null, null, null));
    }
}
