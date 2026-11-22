package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.enrollment.service.AddProgramEnrollmentApplicationDocumentTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class AddProgramEnrollmentApplicationDocumentService implements AddProgramEnrollmentApplicationDocumentUseCase {

    private final AddProgramEnrollmentApplicationDocumentTransaction addDocumentTransaction;

    public AddProgramEnrollmentApplicationDocumentService(AddProgramEnrollmentApplicationDocumentTransaction addDocumentTransaction) {
        this.addDocumentTransaction = addDocumentTransaction;
    }

    @Override
    public StudentDocument add(AddProgramEnrollmentApplicationDocumentCommand command) {
        return addDocumentTransaction.add(command.applicationId(), command.type(), command.file());
    }
}
