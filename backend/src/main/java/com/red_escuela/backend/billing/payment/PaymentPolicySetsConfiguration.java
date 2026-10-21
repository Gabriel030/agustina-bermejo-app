package com.red_escuela.backend.billing.payment;

import com.red_escuela.backend.billing.payment.policy.BankTransferReceiptDetailsCheck;
import com.red_escuela.backend.billing.payment.policy.BankTransferReceiptDetailsRequiredPolicy;
import com.red_escuela.backend.billing.payment.policy.BankTransferReceiptRegistrationCheck;
import com.red_escuela.backend.billing.payment.policy.DuplicateBankTransferPolicy;
import com.red_escuela.backend.billing.payment.policy.ReceiptCoversOldestInvoicesFirstPolicy;
import com.red_escuela.backend.billing.payment.policy.ReceiptProtectionCheck;
import com.red_escuela.backend.billing.payment.policy.ReceiptUploadedByDueDatePolicy;
import com.red_escuela.backend.billing.payment.policy.SelectedInvoicesBelongToAccountPolicy;
import com.red_escuela.backend.billing.payment.policy.CashPaymentAmountPolicy;
import com.red_escuela.backend.billing.payment.policy.CashPaymentBackdateReasonLengthPolicy;
import com.red_escuela.backend.billing.payment.policy.CashPaymentBackdateReasonPolicy;
import com.red_escuela.backend.billing.payment.policy.CashPaymentCheck;
import com.red_escuela.backend.billing.payment.policy.CashPaymentDateNotInFuturePolicy;
import com.red_escuela.backend.billing.payment.policy.CashPaymentDateWithinAdministrativeWindowPolicy;
import com.red_escuela.backend.billing.payment.policy.CashPaymentPreviewCheck;
import com.red_escuela.backend.billing.payment.policy.CashPaymentPreviewIsCurrentPolicy;
import com.red_escuela.backend.billing.payment.policy.CashPaymentReceiptNumberPolicy;
import com.red_escuela.backend.billing.payment.policy.IdempotencyKeyFormatPolicy;
import com.red_escuela.backend.billing.payment.policy.PaymentGatewayAvailabilityContext;
import com.red_escuela.backend.billing.payment.policy.PaymentGatewayAvailabilityPolicy;
import com.red_escuela.backend.billing.payment.policy.PaymentGatewayCapabilityContext;
import com.red_escuela.backend.billing.payment.policy.PaymentGatewayCapabilityPolicy;
import com.red_escuela.backend.billing.payment.policy.IdempotencyKeyReuseConsistencyPolicy;
import com.red_escuela.backend.billing.payment.policy.IdempotencyKeyReuseContext;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PaymentPolicySetsConfiguration {

    @Bean
    public PolicySet<PaymentGatewayAvailabilityContext> paymentGatewayAvailabilityPolicySet() {
        return PolicySet.named("PAYMENT_GATEWAY_RESOLUTION", List.of(new PaymentGatewayAvailabilityPolicy()));
    }

    @Bean
    public PolicySet<PaymentGatewayCapabilityContext> paymentGatewayCapabilityPolicySet() {
        return PolicySet.named("PAYMENT_GATEWAY_CAPABILITY", List.of(new PaymentGatewayCapabilityPolicy()));
    }

    @Bean
    public PolicySet<CashPaymentCheck> cashPaymentRegistrationPolicySet() {
        return PolicySet.named("PAYMENT_REGISTRATION", List.of(new IdempotencyKeyFormatPolicy(), new CashPaymentAmountPolicy(),
                new CashPaymentReceiptNumberPolicy(), new CashPaymentBackdateReasonLengthPolicy()));
    }

    @Bean
    public PolicySet<CashPaymentCheck> cashPaymentDatingPolicySet() {
        return PolicySet.named("CASH_PAYMENT_DATING", List.of(new CashPaymentDateNotInFuturePolicy(), new CashPaymentDateWithinAdministrativeWindowPolicy(),
                new CashPaymentBackdateReasonPolicy()));
    }

    @Bean
    public PolicySet<CashPaymentCheck> cashPaymentPreviewRequestPolicySet() {
        return PolicySet.named("CASH_PAYMENT_PREVIEW_REQUEST", List.of(new CashPaymentAmountPolicy()));
    }

    @Bean
    public PolicySet<CashPaymentPreviewCheck> cashPaymentPreviewPolicySet() {
        return PolicySet.named("CASH_PAYMENT_PREVIEW", List.of(new CashPaymentPreviewIsCurrentPolicy()));
    }

    @Bean
    public PolicySet<CashPaymentCheck> bankTransferReceiptIdempotencyKeyPolicySet() {
        return PolicySet.named("BANK_TRANSFER_RECEIPT_IDEMPOTENCY_KEY", List.of(new IdempotencyKeyFormatPolicy()));
    }

    @Bean
    public PolicySet<BankTransferReceiptDetailsCheck> bankTransferReceiptDetailsPolicySet() {
        return PolicySet.named("BANK_TRANSFER_RECEIPT_DETAILS", List.of(new BankTransferReceiptDetailsRequiredPolicy()));
    }

    @Bean
    public PolicySet<BankTransferReceiptRegistrationCheck> bankTransferReceiptRegistrationPolicySet() {
        return PolicySet.named("BANK_TRANSFER_RECEIPT_REGISTRATION", List.of(new SelectedInvoicesBelongToAccountPolicy(), new DuplicateBankTransferPolicy(),
                new ReceiptCoversOldestInvoicesFirstPolicy()));
    }

    @Bean
    public PolicySet<ReceiptProtectionCheck> bankTransferReceiptProtectionPolicySet() {
        return PolicySet.named("BANK_TRANSFER_RECEIPT_PROTECTION", List.of(new ReceiptUploadedByDueDatePolicy()));
    }

    @Bean
    public PolicySet<IdempotencyKeyReuseContext> idempotencyKeyReusePolicySet() {
        return PolicySet.named("IDEMPOTENCY_KEY_REUSE", List.of(new IdempotencyKeyReuseConsistencyPolicy()));
    }
}
