package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.entity.Student;

public interface GetStudentRecordUseCase {
    Student getRecord(Long studentId);
}
