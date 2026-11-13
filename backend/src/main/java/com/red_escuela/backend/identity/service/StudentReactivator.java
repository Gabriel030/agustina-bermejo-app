package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.domain.StudentStatusChangeRecorder;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.events.StudentReactivatedEvent;
import com.red_escuela.backend.identity.infrastructure.AccountAccessStateCache;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.exception.StudentNotWithdrawnException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentReactivator {

    private final StudentRepository studentRepository;
    private final StudentStatusChangeRecorder studentStatusChangeRecorder;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountAccessStateCache accountAccessStateCache;

    public StudentReactivator(StudentRepository studentRepository, StudentStatusChangeRecorder studentStatusChangeRecorder,
            ApplicationEventPublisher eventPublisher, AccountAccessStateCache accountAccessStateCache) {
        this.studentRepository = studentRepository;
        this.studentStatusChangeRecorder = studentStatusChangeRecorder;
        this.eventPublisher = eventPublisher;
        this.accountAccessStateCache = accountAccessStateCache;
    }

    @Transactional
    public void reactivate(Long studentId, String reason) {
        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        if (student.getPerson().getStatus() != StatusType.DELETED) {
            throw new StudentNotWithdrawnException(studentId);
        }
        student.getPerson().setStatus(StatusType.ACTIVE);
        student.setWithdrawalReason(null);
        student.setWithdrawalDetail(null);
        student.setWithdrawnAt(null);
        studentRepository.save(student);
        accountAccessStateCache.evictNowAndAfterCommit(student.getPerson().getId());
        studentStatusChangeRecorder.recordReactivation(student, reason);
        eventPublisher.publishEvent(StudentReactivatedEvent.of(student.getId(), student.getPerson().getId()));
    }
}
