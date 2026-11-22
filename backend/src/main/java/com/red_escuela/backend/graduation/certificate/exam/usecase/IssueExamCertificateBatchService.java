package com.red_escuela.backend.graduation.certificate.exam.usecase;

import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateBatchResponse;
import com.red_escuela.backend.graduation.certificate.exam.dto.IssueExamCertificateBatchRequest;
import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class IssueExamCertificateBatchService implements IssueExamCertificateBatchUseCase {

    @Override
    public ExamCertificateBatchResponse issueBatch(Long examBoardId, IssueExamCertificateBatchRequest request) {
        throw new DocumentOperationPendingException();
    }
}
