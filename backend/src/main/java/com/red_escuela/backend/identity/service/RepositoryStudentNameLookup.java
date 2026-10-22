package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.api.StudentNameLookup;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RepositoryStudentNameLookup implements StudentNameLookup {

    private final StudentRepository studentRepository;

    public RepositoryStudentNameLookup(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> findFullNamesByStudentId(Collection<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        return studentRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, student -> student.getPerson().getName() + " " + student.getPerson().getLastname(), (first, second) -> first));
    }
}
