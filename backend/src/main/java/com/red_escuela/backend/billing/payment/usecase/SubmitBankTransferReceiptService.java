package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.dto.BankTransferReceiptSubmissionRequest;
import com.red_escuela.backend.billing.payment.service.BankTransferReceiptSubmissionRunner;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class SubmitBankTransferReceiptService implements SubmitBankTransferReceiptUseCase {
    private final BankTransferReceiptSubmissionRunner submissionRunner;

    public SubmitBankTransferReceiptService(BankTransferReceiptSubmissionRunner submissionRunner) {
        this.submissionRunner = submissionRunner;
    }

    @Override
    @PreAuthorize("hasRole('ALUMNO')")
    public BankTransferReceiptSubmissionResult submitReceiptOfOwnAccount(Long ownStudentId, BankTransferReceiptSubmissionRequest request, String idempotencyKey) {
        return submissionRunner.runSubmission(ownStudentId, request, idempotencyKey);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public BankTransferReceiptSubmissionResult submitReceiptOnBehalfOfStudent(Long studentId, BankTransferReceiptSubmissionRequest request, String idempotencyKey) {
        return submissionRunner.runSubmission(studentId, request, idempotencyKey);
    }
}
