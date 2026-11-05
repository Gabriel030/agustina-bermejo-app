package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.service.PasswordResetCodeRequester;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ResetStudentPasswordService implements ResetStudentPasswordUseCase {

    private final StudentRepository studentRepository;
    private final PasswordResetCodeRequester passwordResetCodeRequester;

    public ResetStudentPasswordService(StudentRepository studentRepository, PasswordResetCodeRequester passwordResetCodeRequester) {
        this.studentRepository = studentRepository;
        this.passwordResetCodeRequester = passwordResetCodeRequester;
    }

    @Override
    public void resetPassword(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        passwordResetCodeRequester.requestPasswordResetCode(student.getPerson().emailValue());
    }
}
