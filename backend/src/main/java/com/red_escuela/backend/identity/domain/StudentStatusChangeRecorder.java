package com.red_escuela.backend.identity.domain;

import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentStatusChange;
import com.red_escuela.backend.identity.entity.StudentStatusChangeType;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.repository.StudentStatusChangeRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class StudentStatusChangeRecorder {

    private final StudentStatusChangeRepository studentStatusChangeRepository;

    private final StudentRepository studentRepository;

    public StudentStatusChangeRecorder(StudentStatusChangeRepository studentStatusChangeRepository,
                                       StudentRepository studentRepository) {
        this.studentStatusChangeRepository = studentStatusChangeRepository;
        this.studentRepository = studentRepository;
    }

    public void recordWithdrawal(Long studentId) {
        Student student = studentRepository.findById(studentId).orElseThrow(() -> new StudentNotFoundException(studentId));
        Long actorPersonId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        LocalDateTime now = ApplicationTime.now();
        student.setWithdrawnAt(now);
        studentStatusChangeRepository.save(new StudentStatusChange(student.getId(), StudentStatusChangeType.WITHDRAWN,
                student.getWithdrawalDetail(), actorPersonId, now));
    }

    public void recordReactivation(Student student, String reason) {
        Long actorPersonId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        studentStatusChangeRepository.save(new StudentStatusChange(student.getId(), StudentStatusChangeType.REACTIVATED, reason,
                actorPersonId, ApplicationTime.now()));
    }
}
