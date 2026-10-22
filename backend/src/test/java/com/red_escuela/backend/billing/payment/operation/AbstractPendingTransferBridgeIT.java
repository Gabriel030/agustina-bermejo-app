package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.operation.PaymentVerification;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.RejectPaymentReceiptUseCase;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.notification.PaymentReceiptApprovedEvent;
import com.red_escuela.backend.notification.PaymentReceiptRejectedEvent;
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

    BankTransferReceipt approve(BankTransferReceiptSubmissionResult submitted) {
        return approveReceipt.approve(submitted.receiptId(), staff, PaymentVerification.none());
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

    List<PaymentReceiptApprovedEvent> approvedEvents() {
        return applicationEvents.stream(PaymentReceiptApprovedEvent.class).toList();
    }

    List<PaymentReceiptRejectedEvent> rejectedEvents() {
        return applicationEvents.stream(PaymentReceiptRejectedEvent.class).toList();
    }
}
