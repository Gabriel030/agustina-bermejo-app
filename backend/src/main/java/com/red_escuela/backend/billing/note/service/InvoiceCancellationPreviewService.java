package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.discount.service.DiscountConsumptionService;
import com.red_escuela.backend.billing.discount.service.EnrollmentFeeDiscountReader;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditAccessService;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.domain.CreditedInvoiceFacts;
import com.red_escuela.backend.billing.note.domain.InvoiceCancellationPreview;
import com.red_escuela.backend.billing.note.exception.CreditNoteInvoiceNotFoundException;
import com.red_escuela.backend.billing.note.policy.InvoiceCancellationContext;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class InvoiceCancellationPreviewService {
    private final InvoiceCreditAccessService invoices;
    private final CreditNoteCommandFactory commandFactory;
    private final PolicySet<InvoiceCancellationContext> cancellationPolicies;
    private final LateFeeAnnulmentOnInstallmentCancellation lateFeeAnnulment;
    private final DiscountConsumptionService discountConsumption;
    private final EnrollmentFeeDiscountReader enrollmentFeeDiscounts;

    public InvoiceCancellationPreviewService(InvoiceCreditAccessService invoices, CreditNoteCommandFactory commandFactory,
                                             PolicySet<InvoiceCancellationContext> cancellationPolicies, LateFeeAnnulmentOnInstallmentCancellation lateFeeAnnulment,
                                             DiscountConsumptionService discountConsumption, EnrollmentFeeDiscountReader enrollmentFeeDiscounts) {
        this.invoices = invoices;
        this.commandFactory = commandFactory;
        this.cancellationPolicies = cancellationPolicies;
        this.lateFeeAnnulment = lateFeeAnnulment;
        this.discountConsumption = discountConsumption;
        this.enrollmentFeeDiscounts = enrollmentFeeDiscounts;
    }

    public InvoiceCancellationPreview previewCancellationOf(Long invoiceId, CreditNoteReason reason, String justification) {
        Invoice invoice = invoices.findInvoice(invoiceId).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(invoiceId));
        CancelInvoiceCommand command = commandFactory.buildCancellationCommandForCurrentUser(invoiceId, reason, justification);
        CreditedInvoiceFacts facts = CreditedInvoiceFacts.of(invoice);
        List<PolicyViolation> blockedBy = new ArrayList<>(cancellationPolicies.evaluateAllRules(new InvoiceCancellationContext(command, facts)).violations());
        blockedBy.addAll(lateFeeAnnulment.findPaymentReviewViolationsOf(invoiceId));
        return new InvoiceCancellationPreview(blockedBy, facts.cancellableAmount(), command.releasesChargeKeys(), command.requestedByDirector(),
                lateFeeAnnulment.describeLiveLateFeesOf(invoiceId), discountConsumption.describeConsumptionReleasedByCancellationOf(invoiceId),
                enrollmentFeeDiscounts.describeEnrollmentFeeOf(invoice));
    }
}
