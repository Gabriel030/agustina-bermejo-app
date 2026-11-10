package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.dto.ProgramApplicationDocumentFile;

public interface GetProgramEnrollmentApplicationDocumentUseCase {
    ProgramApplicationDocumentFile get(Long applicationId, Long documentId);
}
