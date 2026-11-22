package com.red_escuela.backend.graduation.certificate.exam.usecase;

import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateBatchResponse;
import com.red_escuela.backend.graduation.certificate.exam.dto.IssueExamCertificateBatchRequest;

public interface IssueExamCertificateBatchUseCase {

    ExamCertificateBatchResponse issueBatch(Long examBoardId, IssueExamCertificateBatchRequest request);
}
