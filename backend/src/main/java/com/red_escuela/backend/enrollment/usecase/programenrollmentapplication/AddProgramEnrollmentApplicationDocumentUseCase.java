package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.entity.StudentDocument;

public interface AddProgramEnrollmentApplicationDocumentUseCase {
    StudentDocument add(AddProgramEnrollmentApplicationDocumentCommand command);
}
