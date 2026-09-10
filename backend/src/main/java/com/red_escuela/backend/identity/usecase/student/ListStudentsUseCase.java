package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.entity.Student;

import java.util.List;

public interface ListStudentsUseCase {
    List<Student> list(ListStudentsCommand command);
}
