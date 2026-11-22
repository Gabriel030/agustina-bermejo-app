package com.red_escuela.backend.graduation.certificate.exam.usecase;

import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateCandidateResponse;
import java.util.List;

public interface ListExamCertificateCandidatesUseCase {

    List<ExamCertificateCandidateResponse> candidates(Long examBoardId);
}
