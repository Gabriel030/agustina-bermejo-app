package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.payment.dto.BankTransferReceiptSubmissionRequest;
import com.red_escuela.backend.enrollment.api.CourseAdmissionReceiptSubmissionPort;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@Service
public class CourseAdmissionReceiptSubmitter implements CourseAdmissionReceiptSubmissionPort {
    private final BankTransferReceiptSubmissionRunner submissionRunner;

    public CourseAdmissionReceiptSubmitter(BankTransferReceiptSubmissionRunner submissionRunner) {
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
