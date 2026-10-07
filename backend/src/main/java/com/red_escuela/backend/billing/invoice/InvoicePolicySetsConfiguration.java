package com.red_escuela.backend.billing.invoice;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingContext;

@Configuration
public class InvoicePolicySetsConfiguration {

    @Bean
    public PolicySet<InvoiceIssuingContext> issuancePolicySet() {
        List<Policy<InvoiceIssuingContext>> ordered = List.of(
                new ReasonFeeTypePolicy(),
                new DueDateNotBeforeIssueDatePolicy(),
                new EnrollmentFeeSlotRequiredPolicy(),
                new OfferingCurrencyPolicy(),
                new NotAlreadyIssuedPolicy(),
                new EnrollmentFeeSlotWithinTemplateCountPolicy());
        return PolicySet.named("INVOICE_ISSUING", ordered);
    }
}
