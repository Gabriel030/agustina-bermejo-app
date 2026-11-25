package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.StudentSpecifications;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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

    @Transactional(readOnly = true)
    public Optional<Long> findIdByPersonId(Long personId) {
        return studentRepository.findByPerson_Id(personId).map(Student::getId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Student requireByIdForUpdate(Long studentId) {
        return studentRepository.findByIdForUpdate(studentId).orElseThrow(() -> new StudentNotFoundException(studentId));
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentOnboardingSummary> listProspectsPage(String search, int page, int size) {
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), PageResponse.MAX_SIZE);
        Page<Student> students = studentRepository.findAll(
                StudentSpecifications.withOnboardingStatus(StudentOnboardingStatus.PROSPECTO)
                        .and(StudentSpecifications.withProspectSearch(search)),
                PageRequest.of(boundedPage, boundedSize,
                        Sort.by("person.lastname", "person.name").and(Sort.by("id"))));
        return new PageResponse<>(students.getContent().stream().map(StudentOnboardingSummary::of).toList(),
                boundedPage, boundedSize, students.getTotalElements());
    }
}
