package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StudentFinder {
    private final StudentRepository studentRepository;

    public StudentFinder(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public Student requireById(Long studentId) {
        return studentRepository.findById(studentId).orElseThrow(() -> new StudentNotFoundException(studentId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Student requireByIdForUpdate(Long studentId) {
        return studentRepository.findByIdForUpdate(studentId).orElseThrow(() -> new StudentNotFoundException(studentId));
    }
}
