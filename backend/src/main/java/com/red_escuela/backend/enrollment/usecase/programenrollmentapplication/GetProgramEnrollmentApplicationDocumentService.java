package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.dto.ProgramApplicationDocumentFile;
import com.red_escuela.backend.enrollment.service.ProgramApplicationDocumentReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetProgramEnrollmentApplicationDocumentService implements GetProgramEnrollmentApplicationDocumentUseCase {

    private final ProgramApplicationDocumentReader documentReader;

    public GetProgramEnrollmentApplicationDocumentService(ProgramApplicationDocumentReader documentReader) {
        this.documentReader = documentReader;
    }

    @Override
    public ProgramApplicationDocumentFile get(Long applicationId, Long documentId) {
        return documentReader.read(applicationId, documentId);
    }
}
