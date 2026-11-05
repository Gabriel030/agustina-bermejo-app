package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.StudentStatusChange;
import com.red_escuela.backend.identity.entity.StudentStatusChangeType;
import com.red_escuela.backend.identity.repository.StudentStatusChangeRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Service;

@Service
public class StudentStatusChangeRecorder {

    private final StudentStatusChangeRepository studentStatusChangeRepository;

    public StudentStatusChangeRecorder(StudentStatusChangeRepository studentStatusChangeRepository) {
        this.studentStatusChangeRepository = studentStatusChangeRepository;
    }

    public void record(Long studentId, StudentStatusChangeType changeType, String reason, Long actorPersonId) {
        studentStatusChangeRepository.save(new StudentStatusChange(studentId, changeType, reason, actorPersonId,
                ApplicationTime.now()));
    }
}
