package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.specialist.PasswordResetCodeRequester;
import com.red_escuela.backend.identity.specialist.StudentOnlyTargetPolicy;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ResetStudentPasswordService implements ResetStudentPasswordUseCase {

    private final StudentRepository studentRepository;
    private final PasswordResetCodeRequester passwordResetCodeRequester;
    private final StudentOnlyTargetPolicy studentOnlyTargetPolicy;

    public ResetStudentPasswordService(StudentRepository studentRepository, PasswordResetCodeRequester passwordResetCodeRequester,
                                       StudentOnlyTargetPolicy studentOnlyTargetPolicy) {
        this.studentRepository = studentRepository;
        this.passwordResetCodeRequester = passwordResetCodeRequester;
        this.studentOnlyTargetPolicy = studentOnlyTargetPolicy;
    }

    @Override
    public void resetPassword(Long studentId) {
        studentOnlyTargetPolicy.requireStudentOnly(studentId);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        passwordResetCodeRequester.requestPasswordResetCode(student.getPerson().emailValue());
    }
}
