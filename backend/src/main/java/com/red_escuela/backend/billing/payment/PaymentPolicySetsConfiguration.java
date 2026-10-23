package com.red_escuela.backend.billing.payment;

import com.red_escuela.backend.billing.payment.policy.BankTransferReceiptDetailsCheck;
import com.red_escuela.backend.billing.payment.policy.BankTransferReceiptDetailsRequiredPolicy;
import com.red_escuela.backend.billing.payment.policy.BankTransferReceiptFileCheck;
import com.red_escuela.backend.billing.payment.policy.BankTransferReceiptFilePolicy;
import com.red_escuela.backend.billing.payment.policy.BankTransferReceiptRegistrationCheck;
import com.red_escuela.backend.billing.payment.policy.LegacyReceiptNotPendingOnSelectedInvoicesPolicy;
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
import com.red_escuela.backend.billing.payment.policy.LegacyReceiptNotVerifiablePolicy;
import com.red_escuela.backend.billing.payment.policy.PaymentRejectionCheck;
import com.red_escuela.backend.billing.payment.policy.ReceiptApprovalCheck;
import com.red_escuela.backend.billing.payment.policy.ReceiptTransferDateMatchesUploadDatePolicy;
import com.red_escuela.backend.billing.payment.policy.PaymentRejectionReasonRequiredPolicy;
import com.red_escuela.backend.billing.payment.policy.PaymentReviewNoteLengthPolicy;
import com.red_escuela.backend.billing.payment.policy.PaymentVerificationCheck;
import com.red_escuela.backend.billing.payment.policy.VerifiedPaymentAmountPolicy;
import com.red_escuela.backend.billing.payment.policy.VerifiedPaymentCurrencyPolicy;
import com.red_escuela.backend.billing.payment.policy.VerifiedPaymentDatePolicy;
import com.red_escuela.backend.billing.payment.policy.PaymentGatewayAvailabilityPolicy;
import com.red_escuela.backend.billing.payment.policy.PaymentGatewayCapabilityContext;
import com.red_escuela.backend.billing.payment.policy.PaymentGatewayCapabilityPolicy;
import com.red_escuela.backend.billing.payment.policy.IdempotencyKeyReuseConsistencyPolicy;
import com.red_escuela.backend.billing.payment.policy.IdempotencyKeyReuseContext;
import com.red_escuela.backend.billing.payment.policy.ChargebackDateWithinPaymentLifePolicy;
import com.red_escuela.backend.billing.payment.policy.ChargebackIsForTheFullAmountPolicy;
import com.red_escuela.backend.billing.payment.policy.ChargebackReferenceRequiredPolicy;
import com.red_escuela.backend.billing.payment.policy.PaymentMoneyNotRefundedPolicy;
import com.red_escuela.backend.billing.payment.policy.PaymentReversalCheck;
import com.red_escuela.backend.billing.payment.policy.RefundAmountPolicy;
import com.red_escuela.backend.billing.payment.policy.RefundAvailabilityCheck;
import com.red_escuela.backend.billing.payment.policy.RefundCompletionCheck;
import com.red_escuela.backend.billing.payment.policy.RefundCompletionPolicy;
import com.red_escuela.backend.billing.payment.policy.RefundDestinationCheck;
import com.red_escuela.backend.billing.payment.policy.RefundDestinationFormatPolicy;
import com.red_escuela.backend.billing.payment.policy.RefundDestinationRequiredPolicy;
import com.red_escuela.backend.billing.payment.policy.RefundFailureCheck;
import com.red_escuela.backend.billing.payment.policy.RefundFailureReasonPolicy;
import com.red_escuela.backend.billing.payment.policy.RefundNotGreaterThanAvailablePolicy;
import com.red_escuela.backend.billing.payment.policy.RefundRequestCheck;
import com.red_escuela.backend.billing.payment.policy.RefundSameMethodAsOriginalPolicy;
import com.red_escuela.backend.billing.payment.policy.RefundWithoutOverdueDebtPolicy;
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
    public PolicySet<BankTransferReceiptFileCheck> bankTransferReceiptFilePolicySet() {
        return PolicySet.named("BANK_TRANSFER_RECEIPT_FILE", List.of(new BankTransferReceiptFilePolicy()));
    }

    @Bean
    public PolicySet<BankTransferReceiptRegistrationCheck> bankTransferReceiptRegistrationPolicySet() {
        return PolicySet.named("BANK_TRANSFER_RECEIPT_REGISTRATION", List.of(new SelectedInvoicesBelongToAccountPolicy(),
                new LegacyReceiptNotPendingOnSelectedInvoicesPolicy(), new DuplicateBankTransferPolicy(), new ReceiptCoversOldestInvoicesFirstPolicy()));
    }

    @Bean
    public PolicySet<ReceiptProtectionCheck> bankTransferReceiptProtectionPolicySet() {
        return PolicySet.named("BANK_TRANSFER_RECEIPT_PROTECTION", List.of(new ReceiptUploadedByDueDatePolicy()));
    }

    @Bean
    public PolicySet<IdempotencyKeyReuseContext> idempotencyKeyReusePolicySet() {
        return PolicySet.named("IDEMPOTENCY_KEY_REUSE", List.of(new IdempotencyKeyReuseConsistencyPolicy()));
    }

    @Bean
    public PolicySet<PaymentVerificationCheck> paymentVerificationPolicySet() {
        return PolicySet.named("PAYMENT_REVIEW", List.of(new VerifiedPaymentAmountPolicy(), new VerifiedPaymentCurrencyPolicy(), new VerifiedPaymentDatePolicy(),
                new PaymentReviewNoteLengthPolicy()));
    }

    @Bean
    public PolicySet<PaymentRejectionCheck> paymentRejectionReasonPolicySet() {
        return PolicySet.named("PAYMENT_REJECTION", List.of(new PaymentRejectionReasonRequiredPolicy()));
    }

    @Bean
    public PolicySet<PaymentReversalCheck> paymentApprovalRevocationPolicySet() {
        return PolicySet.named("PAYMENT_APPROVAL_REVOCATION", List.of(new PaymentMoneyNotRefundedPolicy()));
    }

    @Bean
    public PolicySet<PaymentReversalCheck> chargebackRegistrationPolicySet() {
        return PolicySet.named("CHARGEBACK_REGISTRATION", List.of(new ChargebackReferenceRequiredPolicy(), new ChargebackDateWithinPaymentLifePolicy(),
                new PaymentMoneyNotRefundedPolicy(), new ChargebackIsForTheFullAmountPolicy()));
    }

    @Bean
    public PolicySet<RefundRequestCheck> refundRequestFormatPolicySet() {
        return PolicySet.named("REFUND_REQUEST_FORMAT", List.of(new RefundAmountPolicy(), new RefundDestinationFormatPolicy()));
    }

    @Bean
    public PolicySet<RefundAvailabilityCheck> refundAvailabilityPolicySet() {
        return PolicySet.named("REFUND_AVAILABILITY", List.of(new RefundWithoutOverdueDebtPolicy(), new RefundNotGreaterThanAvailablePolicy()));
    }

    @Bean
    public PolicySet<RefundDestinationCheck> refundDestinationPolicySet() {
        return PolicySet.named("REFUND_DESTINATION", List.of(new RefundDestinationRequiredPolicy()));
    }

    @Bean
    public PolicySet<RefundCompletionCheck> refundCompletionPolicySet() {
        return PolicySet.named("REFUND_COMPLETION", List.of(new RefundCompletionPolicy(), new RefundSameMethodAsOriginalPolicy()));
    }

    @Bean
    public PolicySet<RefundFailureCheck> refundFailurePolicySet() {
        return PolicySet.named("REFUND_FAILURE", List.of(new RefundFailureReasonPolicy()));
    }

    @Bean
    public PolicySet<ReceiptApprovalCheck> receiptApprovalPolicySet() {
        return PolicySet.named("RECEIPT_APPROVAL", List.of(new LegacyReceiptNotVerifiablePolicy(), new ReceiptTransferDateMatchesUploadDatePolicy()));
    }
}
