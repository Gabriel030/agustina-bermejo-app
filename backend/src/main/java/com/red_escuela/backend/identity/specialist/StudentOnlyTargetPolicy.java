package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentOnlyTargetPolicy {

    private static final String STUDENT_ROLE = "ALUMNO";

    private final StudentRepository studentRepository;

    public StudentOnlyTargetPolicy(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public void requireStudentOnly(Long studentId) {
        boolean holdsOtherRole = studentRepository.findById(studentId)
                .map(student -> student.getPerson().getRoles().stream()
                        .map(Role::getName)
                        .anyMatch(roleName -> !STUDENT_ROLE.equals(roleName)))
                .orElse(false);
        if (holdsOtherRole) {
            throw new StudentNotFoundException(studentId);
        }
    }
}
