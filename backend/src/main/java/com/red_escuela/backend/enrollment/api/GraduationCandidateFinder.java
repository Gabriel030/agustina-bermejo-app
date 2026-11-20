package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@Transactional(readOnly = true)
public class GraduationCandidateFinder {

    private static final Set<EnrollmentStatus> REJECTED_COURSE_STATUSES = Set.of(
            EnrollmentStatus.WITHDRAWN, EnrollmentStatus.WAITLISTED, EnrollmentStatus.WAITLIST_EXPIRED);

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    public GraduationCandidateFinder(ProgramEnrollmentRepository programEnrollmentRepository,
                                     CourseEnrollmentRepository courseEnrollmentRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    public List<GraduationCandidateView> findCompletedProgramCandidates(String query) {
        return programEnrollmentRepository.findGraduationCandidates(likePattern(query)).stream()
                .map(row -> new GraduationCandidateView(row.getEnrollmentId(), row.getStudentId(),
                        fullName(row.getLastname(), row.getName()), row.getDocumentType().label(), row.getDocumentNumber(),
                        row.getTitleName()))
                .toList();
    }

    public List<GraduationCandidateView> findCourseCandidates(String query) {
        return courseEnrollmentRepository.findGraduationCandidates(REJECTED_COURSE_STATUSES, likePattern(query)).stream()
                .map(row -> new GraduationCandidateView(row.getEnrollmentId(), row.getStudentId(),
                        fullName(row.getLastname(), row.getName()), row.getDocumentType().label(), row.getDocumentNumber(),
                        row.getTitleName()))
                .toList();
    }

    private String fullName(String lastname, String name) {
        return (lastname + ", " + name).toUpperCase(Locale.ROOT);
    }

    private String likePattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String escaped = query.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
