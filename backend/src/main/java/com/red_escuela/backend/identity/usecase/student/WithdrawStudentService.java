package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.usecase.person.RevokeAllSessionsUseCase;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.entity.StudentStatusChangeType;
import com.red_escuela.backend.identity.service.StudentStatusChangeRecorder;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class WithdrawStudentService implements WithdrawStudentUseCase {

    private final StudentRepository studentRepository;
    private final RevokeAllSessionsUseCase revokeAllSessionsUseCase;

    private final StudentStatusChangeRecorder studentStatusChangeRecorder;
    private final CurrentActorFinder currentActorFinder;

    public WithdrawStudentService(StudentRepository studentRepository, RevokeAllSessionsUseCase revokeAllSessionsUseCase,
            StudentStatusChangeRecorder studentStatusChangeRecorder, CurrentActorFinder currentActorFinder) {
        this.studentRepository = studentRepository;
        this.revokeAllSessionsUseCase = revokeAllSessionsUseCase;
        this.studentStatusChangeRecorder = studentStatusChangeRecorder;
        this.currentActorFinder = currentActorFinder;
    }

    @Override
    @Transactional
    public void withdraw(WithdrawStudentCommand command) {
        Student student = studentRepository.findById(command.studentId())
                .orElseThrow(() -> new StudentNotFoundException(command.studentId()));
        student.getPerson().setStatus(StatusType.DELETED);
        student.setWithdrawalReason(command.request().reason());
        student.setWithdrawalDetail(command.request().detail());
        student.setWithdrawnAt(ApplicationTime.now());
        studentRepository.save(student);
        studentStatusChangeRecorder.record(student.getId(), StudentStatusChangeType.WITHDRAWN,
                command.request().detail(), currentActorFinder.currentPersonId());
        revokeAllSessionsUseCase.revokeAll(student.getPerson());
    }
}
