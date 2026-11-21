package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.dto.WithdrawStudentRequest;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.specialist.SessionRevoker;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawStudentTransaction {
    private final StudentRepository studentRepository;
    private final SessionRevoker sessionRevoker;

    public WithdrawStudentTransaction(StudentRepository studentRepository, SessionRevoker sessionRevoker) {
        this.studentRepository = studentRepository;
        this.sessionRevoker = sessionRevoker;
    }

    @Transactional
    public void withdraw(Long studentId, WithdrawStudentRequest request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        student.getPerson().setStatus(StatusType.DELETED);
        student.setWithdrawalReason(request.reason());
        student.setWithdrawalDetail(request.detail());
        studentRepository.save(student);
        sessionRevoker.revokeAllSessions(student.getPerson());
    }
}
