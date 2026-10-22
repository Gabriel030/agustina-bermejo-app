package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.operation.SubmitBankTransferReceiptCommand;
import com.red_escuela.backend.billing.payment.operation.SubmitBankTransferReceiptOperation;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.RejectPaymentReceiptUseCase;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

class ProtectedInvoiceLateFeeIT extends AbstractLateFeeScenarioIT {

    @Autowired
    BillingOperationPipeline pipeline;
    @Autowired
    SubmitBankTransferReceiptOperation submitOperation;
    @Autowired
    ApprovePaymentReceiptUseCase approveReceipt;
    @Autowired
    RejectPaymentReceiptUseCase rejectReceipt;

    private BankTransferReceiptSubmissionResult submitOnTime(Invoice origin, String operationNumber) {
        SubmitBankTransferReceiptCommand command = new SubmitBankTransferReceiptCommand(student.getId(), List.of(origin.getId()), ars("100000.00"),
                LocalDate.of(2027, 3, 9), operationNumber, "0170099220000067797370", "Ana Pérez", "Banco Nación", "sha-" + operationNumber,
                studentId -> "students/" + studentId + "/comprobantes_" + operationNumber + ".pdf", "key-" + operationNumber, ACTOR);
        return ((BillingOperationOutcome.Processed<BankTransferReceiptSubmissionResult>) pipeline.runOperation(submitOperation, command)).result();
    }

    private Invoice protectedInvoiceWithMonthEndsPassed(String operationNumber, BankTransferReceiptSubmissionResult[] submitted) {
        arrange(2027);
        publish(5, "0.002", 10, true, true, d(2027, 1, 1));
        MutableClock.INSTANCE.setDate(d(2027, 3, 9));
        Invoice origin = pendingInstallmentDueOn(d(2027, 3, 10));
        submitted[0] = submitOnTime(origin, operationNumber);
        assertThat(reload(origin).getPaymentStatus().name()).isEqualTo("PAYMENT_UNDER_REVIEW");
        MutableClock.INSTANCE.setDate(d(2027, 4, 1));
        reconcile(origin, d(2027, 3, 20));
        reconcile(origin, d(2027, 4, 1));
        return origin;
    }

    @Test
    @Tag("D-154")
    @Tag("D-243")
    void protectedInvoiceAccruesNoLateFeeWhileUnderReviewAndNoneAfterApproval() {
        BankTransferReceiptSubmissionResult[] submitted = new BankTransferReceiptSubmissionResult[1];
        Invoice origin = protectedInvoiceWithMonthEndsPassed("OP-LATE-APPROVE", submitted);

        assertThat(chargesOf(origin)).isEmpty();
        assertThat(reload(origin).getPaymentStatus().name()).isEqualTo("PAYMENT_UNDER_REVIEW");

        MutableClock.INSTANCE.setDate(d(2027, 4, 2));
        approveReceipt.approve(submitted[0].receiptId(), person);
        awaitEventsDrained();
        reconcile(origin, d(2027, 4, 2));
        reconcile(origin, d(2027, 5, 1));

        assertThat(reload(origin).getPaymentStatus().name()).isEqualTo("PAID");
        assertThat(chargesOf(origin)).isEmpty();
        assertScenarioInvariants(d(2027, 5, 1));
    }

    @Test
    @Tag("D-208")
    @Tag("D-154")
    void rejectedAfterTheDueDateReturnsToOverdueAndOnlyThenTheLateFeeRuns() {
        BankTransferReceiptSubmissionResult[] submitted = new BankTransferReceiptSubmissionResult[1];
        Invoice origin = protectedInvoiceWithMonthEndsPassed("OP-LATE-REJECT", submitted);
        assertThat(chargesOf(origin)).isEmpty();

        MutableClock.INSTANCE.setDate(d(2027, 4, 2));
        rejectReceipt.reject(submitted[0].receiptId(), "Comprobante ilegible", person);
        awaitEventsDrained();
        reconcile(origin, d(2027, 5, 1));

        assertThat(reload(origin).getPaymentStatus().name()).isEqualTo("OVERDUE");
        assertThat(chargesOf(origin)).isNotEmpty();
        assertThat(reload(origin).getDueDate()).isEqualTo(d(2027, 3, 10));
    }
}
