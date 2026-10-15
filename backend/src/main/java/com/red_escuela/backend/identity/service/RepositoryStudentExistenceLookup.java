package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.api.StudentExistenceLookup;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RepositoryStudentExistenceLookup implements StudentExistenceLookup {

    private final StudentRepository studentRepository;

    public RepositoryStudentExistenceLookup(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireExistingStudent(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new StudentNotFoundException(studentId);
        }
    }
}
