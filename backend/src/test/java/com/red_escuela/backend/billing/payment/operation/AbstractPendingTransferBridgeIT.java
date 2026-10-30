package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.operation.PaymentVerification;

import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.payment.domain.CashPaymentPreview;
import com.red_escuela.backend.billing.payment.domain.CashPaymentRegistrationResult;
import com.red_escuela.backend.billing.payment.service.CashPaymentPreviewService;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.RejectPaymentReceiptUseCase;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.notification.PaymentReceiptApprovedNotification;
import com.red_escuela.backend.notification.PaymentReceiptRejectedNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RecordApplicationEvents
abstract class AbstractPendingTransferBridgeIT extends AbstractBankTransferReceiptIT {

    @Autowired
    ApprovePaymentReceiptUseCase approveReceipt;
    @Autowired
    RejectPaymentReceiptUseCase rejectReceipt;
    @Autowired
    StudentRepository studentRepository;
    @Autowired
    ApplicationEvents applicationEvents;
    @Autowired
    RegisterCashPaymentOperation cashOperation;
    @Autowired
    CashPaymentPreviewService cashPreviewService;
    @Autowired
    CurrentAccountAllocationService allocationService;

    CashPaymentPreview previewCashOf(Debtor debtor, String amount) {
        return inTransaction(() -> cashPreviewService.previewCashPaymentOfStudent(debtor.studentId(), ars(amount)));
    }

    CashPaymentRegistrationResult registerCash(Debtor debtor, String amount, CashPaymentPreview preview) {
        RegisterCashPaymentCommand command = new RegisterCashPaymentCommand(debtor.studentId(), ars(amount), null, null, null, preview.fingerprint(), false,
                nextKey(), actor);
        return ((BillingOperationOutcome.Processed<CashPaymentRegistrationResult>) pipeline.runOperation(cashOperation, command)).result();
    }

    BankTransferReceipt approve(BankTransferReceiptSubmissionResult submitted) {
        return approveReceipt.approve(submitted.receiptId(), staff, PaymentVerification.none());
    }

    BankTransferReceipt approve(BankTransferReceiptSubmissionResult submitted, PaymentVerification verification) {
        return approveReceipt.approve(submitted.receiptId(), staff, verification);
    }

    BankTransferReceipt reject(BankTransferReceiptSubmissionResult submitted, String reason) {
        return rejectReceipt.reject(submitted.receiptId(), reason, staff);
    }

    BankTransferReceiptSubmissionResult submitOn(Debtor debtor, List<Long> invoiceIds, String amount, String operationNumber) {
        return submit(command(debtor, invoiceIds, amount, TODAY, operationNumber));
    }

    Map<String, Object> paymentRow(Long paymentId) {
        return jdbc.queryForMap("select status, amount, approved_amount, paid_on, rejection_reason, method_details ->> 'operationNumber' as operation_number from payment where id = ?",
                paymentId);
    }

    long paymentsOf(Long accountId) {
        return jdbc.queryForObject("select count(*) from payment where student_current_account_id = ?", Long.class, accountId);
    }

    String receiptStatus(Long receiptId) {
        return jdbc.queryForObject("select status from bank_transfer_receipt where id = ?", String.class, receiptId);
    }

    LocalDate dueDateOfInvoice(Long invoiceId) {
        return jdbc.queryForObject("select due_date from invoice where id = ?", LocalDate.class, invoiceId);
    }

    Person studentPersonOf(Debtor debtor) {
        return inTransaction(() -> studentRepository.findById(debtor.studentId()).orElseThrow().getPerson());
    }

    List<PaymentReceiptApprovedNotification> approvedEvents() {
        return applicationEvents.stream(PaymentReceiptApprovedNotification.class).toList();
    }

    List<PaymentReceiptRejectedNotification> rejectedEvents() {
        return applicationEvents.stream(PaymentReceiptRejectedNotification.class).toList();
    }
}
