package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.api.StudentAttendanceRisk;
import com.red_escuela.backend.identity.api.StudentAttendanceRiskPort;
import com.red_escuela.backend.identity.api.StudentMissingDocumentsPort;
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
import java.util.Map;

@Component
@Transactional(readOnly = true)
public class StudentPageReader {

    private static final String STUDENT_ROLE = "ALUMNO";

    private final StudentRepository studentRepository;

    private final StudentAttendanceRiskPort attendanceRiskPort;
    private final StudentMissingDocumentsPort missingDocumentsPort;

    public StudentPageReader(StudentRepository studentRepository, StudentAttendanceRiskPort attendanceRiskPort,
                             StudentMissingDocumentsPort missingDocumentsPort) {
        this.studentRepository = studentRepository;
        this.attendanceRiskPort = attendanceRiskPort;
        this.missingDocumentsPort = missingDocumentsPort;
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
        List<Long> studentIds = students.getContent().stream().map(Student::getId).toList();
        Map<Long, StudentAttendanceRisk> risks = attendanceRiskPort.riskByStudent(studentIds);
        Map<Long, List<String>> missingDocuments = missingDocumentsPort.missingRequiredDocumentsByStudent(studentIds);
        List<StudentResponse> items = students.getContent().stream()
                .map(student -> StudentResponse.from(student, includeWithdrawalReason,
                        risks.getOrDefault(student.getId(), StudentAttendanceRisk.NONE),
                        missingDocuments.getOrDefault(student.getId(), List.of()))).toList();
        return new PageResponse<>(items, boundedPage, boundedSize, students.getTotalElements());
    }
}
