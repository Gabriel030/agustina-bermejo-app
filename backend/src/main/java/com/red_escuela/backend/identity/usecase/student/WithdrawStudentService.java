package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.usecase.person.RevokeAllSessionsUseCase;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class WithdrawStudentService implements WithdrawStudentUseCase {

    private final StudentRepository studentRepository;
    private final RevokeAllSessionsUseCase revokeAllSessionsUseCase;

    public WithdrawStudentService(StudentRepository studentRepository, RevokeAllSessionsUseCase revokeAllSessionsUseCase) {
        this.studentRepository = studentRepository;
        this.revokeAllSessionsUseCase = revokeAllSessionsUseCase;
    }

    @Override
    @Transactional
    public void withdraw(WithdrawStudentCommand command) {
        Student student = studentRepository.findById(command.studentId())
                .orElseThrow(() -> new StudentNotFoundException(command.studentId()));
        student.getPerson().setStatus(StatusType.DELETED);
        student.setWithdrawalReason(command.request().reason());
        student.setWithdrawalDetail(command.request().detail());
        studentRepository.save(student);
        revokeAllSessionsUseCase.revokeAll(student.getPerson());
    }
}
