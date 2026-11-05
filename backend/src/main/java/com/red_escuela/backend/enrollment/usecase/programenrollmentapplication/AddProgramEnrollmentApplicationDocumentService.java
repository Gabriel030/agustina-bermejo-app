package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.enrollment.service.ProgramApplicationDocumentEditableGuard;
import com.red_escuela.backend.enrollment.service.ProgramApplicationDocumentRecorder;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentApplicationFinder;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class AddProgramEnrollmentApplicationDocumentService implements AddProgramEnrollmentApplicationDocumentUseCase {

    private final ProgramEnrollmentApplicationFinder applicationFinder;
    private final ProgramApplicationDocumentRecorder documentRecorder;
    private final ProgramApplicationDocumentEditableGuard editableGuard;

    public AddProgramEnrollmentApplicationDocumentService(ProgramEnrollmentApplicationFinder applicationFinder,
                                                            ProgramApplicationDocumentRecorder documentRecorder,
                                                            ProgramApplicationDocumentEditableGuard editableGuard) {
        this.applicationFinder = applicationFinder;
        this.documentRecorder = documentRecorder;
        this.editableGuard = editableGuard;
    }

    @Override
    @Transactional
    public StudentDocument add(AddProgramEnrollmentApplicationDocumentCommand command) {
        ProgramEnrollmentApplication application = applicationFinder.findOrThrow(command.applicationId());
        editableGuard.requireEditable(application);
        return documentRecorder.record(application, command.type(), command.file());
    }
}
