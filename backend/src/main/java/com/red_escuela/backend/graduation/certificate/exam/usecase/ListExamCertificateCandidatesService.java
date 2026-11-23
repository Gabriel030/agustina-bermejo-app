package com.red_escuela.backend.graduation.certificate.exam.usecase;

import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateCandidateResponse;
import com.red_escuela.backend.graduation.certificate.exam.specialist.ExamCertificateCandidateLister;
import com.red_escuela.backend.shared.UseCase;
import java.util.List;

@UseCase
public class ListExamCertificateCandidatesService implements ListExamCertificateCandidatesUseCase {

    private final ExamCertificateCandidateLister delegate;

    public ListExamCertificateCandidatesService(ExamCertificateCandidateLister delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<ExamCertificateCandidateResponse> candidates(Long examBoardId) {
        return delegate.candidates(examBoardId);
    }
}
