package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountContest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.policy.issuing.BestDiscountPerInvoiceTypePolicy;
import com.red_escuela.backend.billing.invoice.policy.issuing.ChargeKeyNotAlreadyUsedPolicy;
import com.red_escuela.backend.billing.invoice.policy.issuing.ChargeRecipientMatchesEnrollmentPolicy;
import com.red_escuela.backend.billing.invoice.policy.issuing.DiscountMatchesInvoiceTypePolicy;
import com.red_escuela.backend.billing.invoice.policy.issuing.DueDateNotBeforeIssueDatePolicy;
import com.red_escuela.backend.billing.invoice.policy.issuing.EnrollmentFeeSlotRequiredPolicy;
import com.red_escuela.backend.billing.invoice.policy.issuing.EnrollmentFeeSlotWithinTemplateCountPolicy;
import com.red_escuela.backend.billing.invoice.policy.issuing.InvoiceIssuingReasonMatchesFeeTypePolicy;
import com.red_escuela.backend.billing.invoice.policy.issuing.OfferingCurrencyPolicy;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.OperationPreHandlers;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class InvoiceIssuingOperationConfiguration {

    @Bean
    public PolicySet<InvoiceIssuingContext> invoiceIssuingPolicySet() {
        List<Policy<InvoiceIssuingContext>> ordered = List.of(
                new InvoiceIssuingReasonMatchesFeeTypePolicy(),
                new DueDateNotBeforeIssueDatePolicy(),
                new EnrollmentFeeSlotRequiredPolicy(),
                new ChargeRecipientMatchesEnrollmentPolicy(),
                new OfferingCurrencyPolicy(),
                new ChargeKeyNotAlreadyUsedPolicy(),
                new EnrollmentFeeSlotWithinTemplateCountPolicy());
        return PolicySet.named("INVOICE_ISSUING", ordered);
    }

    @Bean
    public PolicySet<DiscountContest> invoiceDiscountSelectionPolicySet() {
        List<Policy<DiscountContest>> ordered = List.of(new DiscountMatchesInvoiceTypePolicy(), new BestDiscountPerInvoiceTypePolicy());
        return PolicySet.named("INVOICE_DISCOUNT_SELECTION", ordered);
    }

    @Bean
    public OperationPreHandlers invoiceIssuingPreHandlers(InvoiceDiscountEnrichmentHandler discountEnrichmentHandler) {
        return new OperationPreHandlers(BillingOperationType.ISSUE_INVOICE, List.of(discountEnrichmentHandler));
    }
}
