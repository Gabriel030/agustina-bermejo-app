package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.dto.StudentRecordResponse;

public interface UpdateStudentUseCase {
    StudentRecordResponse update(UpdateStudentCommand command);
}
