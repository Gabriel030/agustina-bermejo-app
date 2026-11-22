package com.red_escuela.backend.graduation.certificate.exam.usecase;

import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateCandidateResponse;
import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.shared.UseCase;
import java.util.List;

@UseCase
public class ListExamCertificateCandidatesService implements ListExamCertificateCandidatesUseCase {

    @Override
    public List<ExamCertificateCandidateResponse> candidates(Long examBoardId) {
        throw new DocumentOperationPendingException();
    }
}
