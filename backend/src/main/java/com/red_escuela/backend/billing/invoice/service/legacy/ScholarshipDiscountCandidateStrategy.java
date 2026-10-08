package com.red_escuela.backend.billing.invoice.service.legacy;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.operation.InvoiceDiscountCandidateStrategy;
import com.red_escuela.backend.billing.invoice.policy.issuing.ScholarshipDiscountAppliesPolicy;
import com.red_escuela.backend.billing.entity.InstallmentStatus;
import com.red_escuela.backend.billing.entity.Scholarship;
import com.red_escuela.backend.billing.repository.ScholarshipRepository;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ScholarshipDiscountCandidateStrategy implements InvoiceDiscountCandidateStrategy {

    private static final int MAX_NAME_LENGTH = 200;

    private final ScholarshipRepository scholarshipRepository;
    private final ScholarshipDiscountAppliesPolicy appliesPolicy = new ScholarshipDiscountAppliesPolicy();

    public ScholarshipDiscountCandidateStrategy(ScholarshipRepository scholarshipRepository) {
        this.scholarshipRepository = scholarshipRepository;
    }

    @Override
    public DiscountType discountType() {
        return DiscountType.SCHOLARSHIP;
    }

    @Override
    public List<InvoiceDiscountCandidate> findDiscountCandidatesForInvoice(InvoiceIssuingContext context) {
        InvoiceIssuingRequest request = context.request();
        return findActiveScholarship(request)
                .filter(scholarship -> appliesPolicy.evaluateRule(new ScholarshipDiscountAppliesPolicy.Offer(request.feeType(),
                        scholarship.getAppliesTo().covers(request.feeType()))).allowed())
                .filter(scholarship -> pendingInstallmentsOf(scholarship) > 0)
                .map(scholarship -> candidateOf(scholarship, context.facts().chargeAmount()))
                .stream()
                .filter(candidate -> !candidate.discountAmount().value().isZero())
                .toList();
    }

    @Override
    public void markDiscountAsApplied(InvoiceDiscountCandidate winner) {
        Scholarship scholarship = scholarshipRepository.findById(winner.sourceId()).orElseThrow();
        scholarship.consumeOneWithoutLegacyInvoice();
        scholarshipRepository.save(scholarship);
    }

    private Optional<Scholarship> findActiveScholarship(InvoiceIssuingRequest request) {
        if (request.programEnrollmentId() != null) {
            return scholarshipRepository.findActiveByProgramEnrollmentIdForUpdate(request.programEnrollmentId());
        }
        return request.courseEnrollmentId() == null ? Optional.empty() : scholarshipRepository.findActiveByCourseEnrollmentIdForUpdate(request.courseEnrollmentId());
    }

    private InvoiceDiscountCandidate candidateOf(Scholarship scholarship, NonNegativeMoney base) {
        int total = scholarship.getInstallments().size();
        long applied = scholarship.getInstallments().stream().filter(installment -> installment.getStatus() == InstallmentStatus.APPLIED).count();
        String name = ("Beca: " + scholarship.getReason());
        return new InvoiceDiscountCandidate(DiscountType.SCHOLARSHIP, scholarship.getId(), scholarship.getId(),
                name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name, InvoiceType.MONTHLY_INSTALLMENT, base,
                DiscountAmounts.discountOf(scholarship.getValue(), base), (int) applied + 1, total);
    }

    private long pendingInstallmentsOf(Scholarship scholarship) {
        return scholarship.getInstallments().stream().filter(installment -> installment.getStatus() == InstallmentStatus.PENDING).count();
    }
}
