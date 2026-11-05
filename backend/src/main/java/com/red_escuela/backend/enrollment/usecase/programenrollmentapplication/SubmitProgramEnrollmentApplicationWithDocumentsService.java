package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse;
import com.red_escuela.backend.enrollment.service.ProgramApplicationWithDocumentsSubmitter;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SubmitProgramEnrollmentApplicationWithDocumentsService implements SubmitProgramEnrollmentApplicationWithDocumentsUseCase {

    private final ProgramApplicationWithDocumentsSubmitter applicationSubmitter;

    public SubmitProgramEnrollmentApplicationWithDocumentsService(ProgramApplicationWithDocumentsSubmitter applicationSubmitter) {
        this.applicationSubmitter = applicationSubmitter;
    }

    @Override
    public ProgramEnrollmentApplicationResponse submit(SubmitProgramEnrollmentApplicationWithDocumentsCommand command) {
        return applicationSubmitter.submit(command.studentId(), command.programId(), command.cohortId(), command.files());
    }
}
