package com.red_escuela.backend.announcement.service.audience;

import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.service.SectionMembership;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SectionSnapshotLoader {

    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final CourseSubjectSectionRepository courseSubjectSectionRepository;
    private final GradeItemRepository gradeItemRepository;
    private final AttendanceRepository attendanceRepository;

    public SectionSnapshotLoader(SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
                                 CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository,
                                 SubjectSectionRepository subjectSectionRepository,
                                 CourseSubjectSectionRepository courseSubjectSectionRepository,
                                 GradeItemRepository gradeItemRepository,
                                 AttendanceRepository attendanceRepository) {
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.courseSubjectSectionRepository = courseSubjectSectionRepository;
        this.gradeItemRepository = gradeItemRepository;
        this.attendanceRepository = attendanceRepository;
    }

    public SectionSnapshot load(SectionType type, Long sectionId) {
        return type == SectionType.PROGRAM ? loadProgram(sectionId) : loadCourse(sectionId);
    }

    private SectionSnapshot loadProgram(Long sectionId) {
        List<SubjectSectionEnrollment> enrollments = subjectSectionEnrollmentRepository
                .findBySubjectSectionIdAndStatusIn(sectionId, SectionMembership.ENROLLED_STATUSES);
        List<Long> ids = enrollments.stream().map(SubjectSectionEnrollment::getId).toList();

        Map<Long, List<GradeItem>> grades = gradesByEnrollment(
                ids.isEmpty() ? List.of() : gradeItemRepository.findByProgramSubjectSectionEnrollmentIdIn(ids),
                item -> item.getProgramSubjectSectionEnrollment().getId());
        Map<Long, long[]> attendance = attendanceByEnrollment(
                ids.isEmpty() ? List.of() : attendanceRepository.countsByProgramEnrollment(ids, AttendanceStatus.ABSENT));

        List<StudentSnapshot> students = new ArrayList<>();
        for (SubjectSectionEnrollment enrollment : enrollments) {
            students.add(toSnapshot(enrollment.getId(), enrollment.getStudent(), enrollment.getStatus(),
                    enrollment.getFinalGradeType(), grades, attendance));
        }

        EvaluationScheme scheme = subjectSectionRepository.findById(sectionId)
                .map(section -> section.getEvaluationScheme()).orElse(null);
        return new SectionSnapshot(students, scheme);
    }

    private SectionSnapshot loadCourse(Long sectionId) {
        List<CourseSubjectSectionEnrollment> enrollments = courseSubjectSectionEnrollmentRepository
                .findByCourseSubjectSectionIdAndStatusIn(sectionId, SectionMembership.ENROLLED_STATUSES);
        List<Long> ids = enrollments.stream().map(CourseSubjectSectionEnrollment::getId).toList();

        Map<Long, List<GradeItem>> grades = gradesByEnrollment(
                ids.isEmpty() ? List.of() : gradeItemRepository.findByCourseSubjectSectionEnrollmentIdIn(ids),
                item -> item.getCourseSubjectSectionEnrollment().getId());
        Map<Long, long[]> attendance = attendanceByEnrollment(
                ids.isEmpty() ? List.of() : attendanceRepository.countsByCourseEnrollment(ids, AttendanceStatus.ABSENT));

        List<StudentSnapshot> students = new ArrayList<>();
        for (CourseSubjectSectionEnrollment enrollment : enrollments) {
            students.add(toSnapshot(enrollment.getId(), enrollment.getStudent(), enrollment.getStatus(),
                    enrollment.getFinalGradeType(), grades, attendance));
        }

        EvaluationScheme scheme = courseSubjectSectionRepository.findById(sectionId)
                .map(section -> section.getEvaluationScheme()).orElse(null);
        return new SectionSnapshot(students, scheme);
    }

    private StudentSnapshot toSnapshot(Long enrollmentId,
                                       com.red_escuela.backend.identity.entity.Student student,
                                       com.red_escuela.backend.enrollment.entity.EnrollmentStatus status,
                                       com.red_escuela.backend.evaluation.entity.FinalGradeType finalGradeType,
                                       Map<Long, List<GradeItem>> grades,
                                       Map<Long, long[]> attendance) {
        List<GradeItem> items = grades.getOrDefault(enrollmentId, List.of());
        long[] counts = attendance.getOrDefault(enrollmentId, new long[]{0L, 0L});
        return new StudentSnapshot(
                student,
                status,
                finalGradeType,
                scoresOf(items, GradeItemType.EXAM),
                scoresOf(items, GradeItemType.TP),
                scoresOf(items, GradeItemType.FINAL),
                counts[1],
                counts[0]);
    }

    private Map<Integer, Double> scoresOf(List<GradeItem> items, GradeItemType type) {
        Map<Integer, Double> scores = new HashMap<>();
        for (GradeItem item : items) {
            if (item.getType() == type && item.getSequenceNumber() != null && item.getScore() != null) {
                scores.put(item.getSequenceNumber(), item.getScore().value().doubleValue());
            }
        }
        return scores;
    }

    private Map<Long, List<GradeItem>> gradesByEnrollment(List<GradeItem> items,
                                                          java.util.function.Function<GradeItem, Long> keyOf) {
        Map<Long, List<GradeItem>> byEnrollment = new HashMap<>();
        for (GradeItem item : items) {
            byEnrollment.computeIfAbsent(keyOf.apply(item), k -> new ArrayList<>()).add(item);
        }
        return byEnrollment;
    }

    private Map<Long, long[]> attendanceByEnrollment(List<Object[]> rows) {
        Map<Long, long[]> byEnrollment = new HashMap<>();
        for (Object[] row : rows) {
            Long enrollmentId = ((Number) row[0]).longValue();
            long total = ((Number) row[1]).longValue();
            long absent = row[2] == null ? 0L : ((Number) row[2]).longValue();
            byEnrollment.put(enrollmentId, new long[]{total, absent});
        }
        return byEnrollment;
    }
}
