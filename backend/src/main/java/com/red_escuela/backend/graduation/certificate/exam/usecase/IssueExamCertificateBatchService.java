package com.red_escuela.backend.graduation.certificate.exam.usecase;

import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateBatchResponse;
import com.red_escuela.backend.graduation.certificate.exam.dto.IssueExamCertificateBatchRequest;
import com.red_escuela.backend.graduation.certificate.exam.specialist.ExamCertificateBatchIssuer;
import com.red_escuela.backend.shared.UseCase;


@UseCase
public class IssueExamCertificateBatchService implements IssueExamCertificateBatchUseCase {

    private final ExamCertificateBatchIssuer delegate;

    public IssueExamCertificateBatchService(ExamCertificateBatchIssuer delegate) {
        this.delegate = delegate;
    }

    @Override
    public ExamCertificateBatchResponse issueBatch(Long examBoardId, IssueExamCertificateBatchRequest request) {
        return delegate.issueBatch(examBoardId, request);
    }
}
