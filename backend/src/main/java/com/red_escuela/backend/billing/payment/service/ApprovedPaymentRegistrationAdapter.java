package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.invoice.service.ReportedInvoiceLookup;
import com.red_escuela.backend.billing.operation.ApprovedPaymentRegistrationPort;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.payment.domain.ApprovedPaymentRegistrationResult;
import com.red_escuela.backend.billing.payment.domain.CardPaymentDetails;
import com.red_escuela.backend.billing.operation.CardPaymentType;
import com.red_escuela.backend.billing.operation.MercadoPagoCheckoutMatch;
import com.red_escuela.backend.billing.payment.domain.GatewayRawStatus;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.operation.ApproveBankTransferReceiptCommand;
import com.red_escuela.backend.billing.payment.operation.ApproveBankTransferReceiptOperation;
import com.red_escuela.backend.billing.payment.operation.RegisterGatewayPaymentCommand;
import com.red_escuela.backend.billing.payment.operation.RegisterGatewayPaymentOperation;
import com.red_escuela.backend.billing.payment.repository.GatewayCheckoutRepository;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class ApprovedPaymentRegistrationAdapter implements ApprovedPaymentRegistrationPort {
    private static final String APPROVED_RAW_STATUS = "approved";

    private final BillingOperationPipeline pipeline;
    private final RegisterGatewayPaymentOperation gatewayOperation;
    private final ApproveBankTransferReceiptOperation transferOperation;
    private final ReportedInvoiceLookup invoices;
    private final PaymentRepository payments;
    private final GatewayCheckoutRepository checkouts;
    private final AuditTrail auditTrail;

    public ApprovedPaymentRegistrationAdapter(BillingOperationPipeline pipeline, RegisterGatewayPaymentOperation gatewayOperation,
                                              ApproveBankTransferReceiptOperation transferOperation, ReportedInvoiceLookup invoices, PaymentRepository payments,
                                              GatewayCheckoutRepository checkouts, AuditTrail auditTrail) {
        this.pipeline = pipeline;
        this.gatewayOperation = gatewayOperation;
        this.transferOperation = transferOperation;
        this.invoices = invoices;
        this.payments = payments;
        this.checkouts = checkouts;
        this.auditTrail = auditTrail;
    }

    @Override
    public void lockMercadoPagoPaymentUntilTransactionEnds(String mercadoPagoPaymentId) {
        payments.lockGatewayPaymentUntilTransactionEnds(mercadoPagoPaymentId);
    }

    @Override
    public boolean isMercadoPagoPaymentRegistered(String mercadoPagoPaymentId) {
        return payments.existsByGatewayAndGatewayPaymentId(PaymentGatewayCode.MERCADO_PAGO, mercadoPagoPaymentId);
    }

    @Override
    public Optional<MercadoPagoCheckoutMatch> findMercadoPagoCheckout(UUID publicReference) {
        return checkouts.findByPublicReference(publicReference)
                .filter(checkout -> checkout.getGateway() == PaymentGatewayCode.MERCADO_PAGO)
                .map(checkout -> new MercadoPagoCheckoutMatch(checkout.getInvoiceId(), checkout.getAmount().value(),
                        ApplicationTime.now().isAfter(checkout.getExpiresAt()), checkout.getCreditedGatewayPaymentId()));
    }

    @Override
    public void recordMercadoPagoAmountMismatch(Long invoiceId, Money expectedAmount, Money paidAmount, String mercadoPagoPaymentId) {
        auditTrail.record(new AuditRecord(AuditAction.GATEWAY_PAYMENT_AMOUNT_MISMATCH, AuditEntityType.INVOICE, invoiceId,
                describe(expectedAmount), describe(paidAmount), "pagoMp=" + mercadoPagoPaymentId));
    }

    private static String describe(Money money) {
        return money.amount().toPlainString() + " " + money.currency();
    }

    @Override
    public void recordMercadoPagoExpiredCheckout(Long invoiceId, String mercadoPagoPaymentId) {
        auditTrail.record(new AuditRecord(AuditAction.GATEWAY_PAYMENT_CHECKOUT_EXPIRED, AuditEntityType.INVOICE, invoiceId,
                null, null, "pagoMp=" + mercadoPagoPaymentId));
    }

    @Override
    public void recordMercadoPagoDuplicatePaymentForCheckout(Long invoiceId, String creditedGatewayPaymentId, String mercadoPagoPaymentId) {
        auditTrail.record(new AuditRecord(AuditAction.GATEWAY_PAYMENT_DUPLICATE_FOR_CHECKOUT, AuditEntityType.INVOICE, invoiceId,
                creditedGatewayPaymentId, mercadoPagoPaymentId, "pagoMp=" + mercadoPagoPaymentId));
    }

    @Override
    public void markMercadoPagoCheckoutCredited(UUID publicReference, String mercadoPagoPaymentId) {
        checkouts.findByPublicReference(publicReference).ifPresent(checkout -> checkout.markCreditedBy(mercadoPagoPaymentId));
    }

    @Override
    public void recordMercadoPagoUnmatchedPayment(String mercadoPagoPaymentId, UUID publicReference) {
        try {
            auditTrail.record(new AuditRecord(AuditAction.GATEWAY_PAYMENT_UNMATCHED, AuditEntityType.GATEWAY_PAYMENT,
                    Long.parseLong(mercadoPagoPaymentId), null, null, "referencia=" + publicReference));
        } catch (NumberFormatException e) {
            return;
        }
    }

    @Override
    public Long registerApprovedMercadoPagoPayment(Long reportedInvoiceId, Money amount, Integer installments, CardPaymentType cardType, String mercadoPagoPaymentId) {
        ReportedInvoiceLookup.ReportedInvoice invoice = invoices.findReportedInvoice(reportedInvoiceId);
        Set<Long> programAdmissionInvoiceIds = invoice.programAdmission() ? Set.of(reportedInvoiceId) : Set.of();
        RegisterGatewayPaymentCommand command = new RegisterGatewayPaymentCommand(invoice.studentCurrentAccountId(), reportedInvoiceId, programAdmissionInvoiceIds,
                amount, PaymentGatewayCode.MERCADO_PAGO, new CardPaymentDetails(cardType, installments, null), mercadoPagoPaymentId,
                new GatewayRawStatus(APPROVED_RAW_STATUS, null), StatusChangeActor.ofPerson(invoice.ownerPersonId()));
        return pipeline.runOperation(gatewayOperation, command).resultOrThrowIfAwaitingAuthorization(requestId -> new IllegalStateException("Unexpected authorization request " + requestId)).paymentId();
    }

    @Override
    public Long registerApprovedBankTransferPayment(Long invoiceId, Long reviewerPersonId) {
        ReportedInvoiceLookup.ReportedInvoice invoice = invoices.findReportedInvoice(invoiceId);
        ApproveBankTransferReceiptCommand command = new ApproveBankTransferReceiptCommand(invoiceId, invoice.studentCurrentAccountId(), invoice.programAdmission(),
                StatusChangeActor.ofPerson(reviewerPersonId));
        ApprovedPaymentRegistrationResult result = pipeline.runOperation(transferOperation, command).resultOrThrowIfAwaitingAuthorization(requestId -> new IllegalStateException("Unexpected authorization request " + requestId));
        return result.paymentId();
    }
}
