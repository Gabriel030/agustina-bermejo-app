package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.enrollment.exception.ApplicationDocumentsLockedException;
import com.red_escuela.backend.enrollment.service.ProgramApplicationDocumentRecorder;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentApplicationFinder;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class AddProgramEnrollmentApplicationDocumentService implements AddProgramEnrollmentApplicationDocumentUseCase {

    private final ProgramEnrollmentApplicationFinder applicationFinder;
    private final ProgramApplicationDocumentRecorder documentRecorder;

    public AddProgramEnrollmentApplicationDocumentService(ProgramEnrollmentApplicationFinder applicationFinder,
                                                            ProgramApplicationDocumentRecorder documentRecorder) {
        this.applicationFinder = applicationFinder;
        this.documentRecorder = documentRecorder;
    }

    @Override
    @Transactional
    public StudentDocument add(AddProgramEnrollmentApplicationDocumentCommand command) {
        ProgramEnrollmentApplication application = applicationFinder.findOrThrow(command.applicationId());
        if (application.getStatus() != ProgramEnrollmentApplicationStatus.PENDING) {
            throw new ApplicationDocumentsLockedException(application.getId(), application.getStatus());
        }
        return documentRecorder.record(application, command.type(), command.file());
    }
}
