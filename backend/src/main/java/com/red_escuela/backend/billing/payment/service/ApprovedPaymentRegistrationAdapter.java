package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.invoice.service.ReportedInvoiceLookup;
import com.red_escuela.backend.billing.operation.ApprovedPaymentRegistrationPort;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.payment.domain.ApprovedPaymentRegistrationResult;
import com.red_escuela.backend.billing.payment.domain.CardPaymentDetails;
import com.red_escuela.backend.billing.operation.CardPaymentType;
import com.red_escuela.backend.billing.payment.domain.GatewayRawStatus;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.operation.ApproveBankTransferReceiptCommand;
import com.red_escuela.backend.billing.payment.operation.ApproveBankTransferReceiptOperation;
import com.red_escuela.backend.billing.payment.operation.RegisterGatewayPaymentCommand;
import com.red_escuela.backend.billing.payment.operation.RegisterGatewayPaymentOperation;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ApprovedPaymentRegistrationAdapter implements ApprovedPaymentRegistrationPort {
    private static final String APPROVED_RAW_STATUS = "approved";

    private final BillingOperationPipeline pipeline;
    private final RegisterGatewayPaymentOperation gatewayOperation;
    private final ApproveBankTransferReceiptOperation transferOperation;
    private final ReportedInvoiceLookup invoices;
    private final PaymentRepository payments;

    public ApprovedPaymentRegistrationAdapter(BillingOperationPipeline pipeline, RegisterGatewayPaymentOperation gatewayOperation,
                                              ApproveBankTransferReceiptOperation transferOperation, ReportedInvoiceLookup invoices, PaymentRepository payments) {
        this.pipeline = pipeline;
        this.gatewayOperation = gatewayOperation;
        this.transferOperation = transferOperation;
        this.invoices = invoices;
        this.payments = payments;
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
