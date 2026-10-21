package com.red_escuela.backend.billing.collections;

import com.red_escuela.backend.billing.collections.domain.LateFeeCalculationStrategy;
import com.red_escuela.backend.billing.collections.domain.DailySimpleLateFeeCalculator;
import com.red_escuela.backend.billing.collections.policy.CorrectionDebitNoteNotUnderPaymentReviewPolicy;
import com.red_escuela.backend.billing.collections.policy.CorrectionTargetsLiveLateFeeDebitNotePolicy;
import com.red_escuela.backend.billing.collections.policy.DebitNoteSameRecipientAsOriginPolicy;
import com.red_escuela.backend.billing.collections.policy.InvoiceIsPastDueDatePolicy;
import com.red_escuela.backend.billing.collections.policy.LateFeeAppliesToInvoiceTypePolicy;
import com.red_escuela.backend.billing.collections.policy.LateFeeChargingEnabledPolicy;
import com.red_escuela.backend.billing.collections.policy.LateFeeCorrectionContext;
import com.red_escuela.backend.billing.collections.policy.LateFeeIssuingContext;
import com.red_escuela.backend.billing.collections.policy.LateFeeNotFrozenPolicy;
import com.red_escuela.backend.billing.collections.policy.OriginNotUnderPaymentReviewPolicy;
import com.red_escuela.backend.billing.collections.policy.OverdueDetectionContext;
import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFact;
import com.red_escuela.backend.billing.collections.policy.PendingCancellationSuppressesReminderPolicy;
import com.red_escuela.backend.billing.collections.policy.ReceiptUnderReviewSuppressesReminderPolicy;
import com.red_escuela.backend.billing.collections.policy.ReminderEligibleInvoicePolicy;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class CollectionsPolicySetsConfiguration {

    @Bean
    public PolicySet<OverdueDetectionContext> overdueDetectionPolicySet() {
        return PolicySet.named("OVERDUE_DETECTION", List.of(new InvoiceIsPastDueDatePolicy()));
    }

    @Bean
    public PolicySet<LateFeeIssuingContext> debitNoteIssuingPolicySet() {
        List<Policy<LateFeeIssuingContext>> ordered = List.of(
                new LateFeeAppliesToInvoiceTypePolicy(),
                new OriginNotUnderPaymentReviewPolicy(),
                new LateFeeChargingEnabledPolicy(),
                new LateFeeNotFrozenPolicy(),
                new DebitNoteSameRecipientAsOriginPolicy());
        return PolicySet.named("DEBIT_NOTE_ISSUING", ordered);
    }

    @Bean
    public PolicySet<LateFeeCorrectionContext> lateFeeCorrectionPolicySet() {
        return PolicySet.named("LATE_FEE_CORRECTION_ISSUING", List.of(new CorrectionTargetsLiveLateFeeDebitNotePolicy(), new CorrectionDebitNoteNotUnderPaymentReviewPolicy()));
    }

    @Bean
    public PolicySet<ReminderInvoiceFact> collectionReminderEligibilityPolicySet() {
        return PolicySet.named("COLLECTION_REMINDER_ELIGIBILITY", List.of(
                new ReminderEligibleInvoicePolicy(),
                new ReceiptUnderReviewSuppressesReminderPolicy(),
                new PendingCancellationSuppressesReminderPolicy()));
    }

    @Bean
    public LateFeeCalculationStrategy lateFeeCalculationStrategy() {
        return new DailySimpleLateFeeCalculator();
    }
}
