package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.dto.BankTransferReceiptSubmissionRequest;
import com.red_escuela.backend.billing.payment.service.BankTransferReceiptSubmissionRunner;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@UseCase
public class SubmitCourseAdmissionReceiptService implements SubmitCourseAdmissionReceiptUseCase {
    private final BankTransferReceiptSubmissionRunner submissionRunner;

    public SubmitCourseAdmissionReceiptService(BankTransferReceiptSubmissionRunner submissionRunner) {
        this.submissionRunner = submissionRunner;
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public Long submitReceiptCoveringCourseAdmission(Long ownStudentId, Long admissionInvoiceId, MultipartFile file, List<Long> olderInvoiceIds,
                                                                    Money declaredAmount, LocalDate transferDate, String operationNumber,
                                                                    String originAccount, String holderName, String bankName, String idempotencyKey) {
        BankTransferReceiptSubmissionRequest request = new BankTransferReceiptSubmissionRequest(file, olderInvoiceIds, declaredAmount, transferDate,
                operationNumber, originAccount, holderName, bankName).withAdditionalInvoice(admissionInvoiceId);
        return submissionRunner.runSubmission(ownStudentId, request, idempotencyKey).receiptId();
    }
}
