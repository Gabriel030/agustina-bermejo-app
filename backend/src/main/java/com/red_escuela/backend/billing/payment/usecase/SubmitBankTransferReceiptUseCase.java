package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.dto.BankTransferReceiptSubmissionRequest;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface SubmitBankTransferReceiptUseCase {

    BankTransferReceiptSubmissionResult submitReceiptOfOwnAccount(Long ownStudentId, BankTransferReceiptSubmissionRequest request, String idempotencyKey);

    BankTransferReceiptSubmissionResult submitReceiptOfOwnCourseAdmission(Long ownStudentId, BankTransferReceiptSubmissionRequest request, String idempotencyKey);

    BankTransferReceiptSubmissionResult submitReceiptOnBehalfOfStudent(Long studentId, BankTransferReceiptSubmissionRequest request, String idempotencyKey);
}
