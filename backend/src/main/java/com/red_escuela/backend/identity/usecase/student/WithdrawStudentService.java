package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class WithdrawStudentService implements WithdrawStudentUseCase {

    private final StudentRepository studentRepository;

    public WithdrawStudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
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
    }
}
