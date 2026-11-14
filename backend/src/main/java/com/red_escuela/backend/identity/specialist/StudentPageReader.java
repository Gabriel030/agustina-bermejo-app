package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.dto.StudentResponse;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.repository.StudentSpecifications;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class StudentPageReader {

    private static final String STUDENT_ROLE = "ALUMNO";

    private final StudentRepository studentRepository;

    public StudentPageReader(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public PageResponse<StudentResponse> readPage(StatusType status, String onboardingStatus, String search,
                                                  int page, int size, boolean includeWithdrawalReason) {
        StudentOnboardingStatus effectiveOnboarding = onboardingStatus == null
                ? StudentOnboardingStatus.CONFIRMADO
                : StudentOnboardingStatus.valueOf(onboardingStatus);

        Specification<Student> spec = Specification
                .where(StudentSpecifications.withPersonStatus(status))
                .and(StudentSpecifications.withOnboardingStatus(effectiveOnboarding))
                .and(StudentSpecifications.withSearch(search));
        if (effectiveOnboarding == StudentOnboardingStatus.CONFIRMADO) {
            spec = spec.and(StudentSpecifications.withRole(STUDENT_ROLE));
        }

        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), PageResponse.MAX_SIZE);
        Page<Student> students = studentRepository.findAll(spec,
                PageRequest.of(boundedPage, boundedSize, Sort.by("person.lastname", "person.name").and(Sort.by("id"))));
        List<StudentResponse> items = students.getContent().stream()
                .map(student -> StudentResponse.from(student, includeWithdrawalReason)).toList();
        return new PageResponse<>(items, boundedPage, boundedSize, students.getTotalElements());
    }
}
