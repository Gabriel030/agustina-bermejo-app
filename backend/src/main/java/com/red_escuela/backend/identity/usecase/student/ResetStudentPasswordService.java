package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.usecase.auth.RequestPasswordResetUseCase;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ResetStudentPasswordService implements ResetStudentPasswordUseCase {

    private final StudentRepository studentRepository;
    private final RequestPasswordResetUseCase requestPasswordResetUseCase;

    public ResetStudentPasswordService(StudentRepository studentRepository, RequestPasswordResetUseCase requestPasswordResetUseCase) {
        this.studentRepository = studentRepository;
        this.requestPasswordResetUseCase = requestPasswordResetUseCase;
    }

    @Override
    public void resetPassword(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        requestPasswordResetUseCase.requestReset(student.getPerson().emailValue());
    }
}
