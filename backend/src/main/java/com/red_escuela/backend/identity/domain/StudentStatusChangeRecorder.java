package com.red_escuela.backend.identity.domain;

import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentStatusChange;
import com.red_escuela.backend.identity.entity.StudentStatusChangeType;
import com.red_escuela.backend.identity.repository.StudentStatusChangeRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class StudentStatusChangeRecorder {

    private final StudentStatusChangeRepository studentStatusChangeRepository;

    public StudentStatusChangeRecorder(StudentStatusChangeRepository studentStatusChangeRepository) {
        this.studentStatusChangeRepository = studentStatusChangeRepository;
    }

    public void recordWithdrawal(Student student, String reason) {
        Long actorPersonId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        LocalDateTime now = ApplicationTime.now();
        student.setWithdrawnAt(now);
        studentStatusChangeRepository.save(new StudentStatusChange(student.getId(), StudentStatusChangeType.WITHDRAWN,
                reason, actorPersonId, now));
    }
}
