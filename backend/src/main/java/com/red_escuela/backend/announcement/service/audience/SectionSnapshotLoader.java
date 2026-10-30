package com.red_escuela.backend.announcement.service.audience;

import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.api.EnrollmentGradeScores;
import com.red_escuela.backend.evaluation.api.EnrollmentGradeScoresFinder;
import com.red_escuela.backend.evaluation.api.SectionEvaluationSchemeFinder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SectionSnapshotLoader {

    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final EnrollmentGradeScoresFinder gradeScoresFinder;
    private final SectionEvaluationSchemeFinder evaluationSchemeFinder;
    private final AttendanceRepository attendanceRepository;

    public SectionSnapshotLoader(SectionEnrollmentFinder sectionEnrollmentFinder,
                                 EnrollmentGradeScoresFinder gradeScoresFinder,
                                 SectionEvaluationSchemeFinder evaluationSchemeFinder,
                                 AttendanceRepository attendanceRepository) {
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.gradeScoresFinder = gradeScoresFinder;
        this.evaluationSchemeFinder = evaluationSchemeFinder;
        this.attendanceRepository = attendanceRepository;
    }

    public SectionSnapshot load(SectionType type, Long sectionId) {
        return type == SectionType.PROGRAM ? loadProgram(sectionId) : loadCourse(sectionId);
    }

    private SectionSnapshot loadProgram(Long sectionId) {
        List<SubjectSectionEnrollment> enrollments = sectionEnrollmentFinder.findMembersOfProgramSection(sectionId);
        List<Long> ids = enrollments.stream().map(SubjectSectionEnrollment::getId).toList();

        Map<Long, EnrollmentGradeScores> grades = gradeScoresFinder.findScoresByProgramEnrollment(ids);
        Map<Long, long[]> attendance = attendanceByEnrollment(
                ids.isEmpty() ? List.of() : attendanceRepository.countsByProgramEnrollment(ids, AttendanceStatus.ABSENT));

        List<StudentSnapshot> students = new ArrayList<>();
        for (SubjectSectionEnrollment enrollment : enrollments) {
            students.add(toSnapshot(enrollment.getId(), enrollment.getStudent(), enrollment.getStatus(),
                    enrollment.getFinalGradeType(), grades, attendance));
        }

        return new SectionSnapshot(students, evaluationSchemeFinder.findProgramSectionScheme(sectionId).orElse(null));
    }

    private SectionSnapshot loadCourse(Long sectionId) {
        List<CourseSubjectSectionEnrollment> enrollments = sectionEnrollmentFinder.findMembersOfCourseSection(sectionId);
        List<Long> ids = enrollments.stream().map(CourseSubjectSectionEnrollment::getId).toList();

        Map<Long, EnrollmentGradeScores> grades = gradeScoresFinder.findScoresByCourseEnrollment(ids);
        Map<Long, long[]> attendance = attendanceByEnrollment(
                ids.isEmpty() ? List.of() : attendanceRepository.countsByCourseEnrollment(ids, AttendanceStatus.ABSENT));

        List<StudentSnapshot> students = new ArrayList<>();
        for (CourseSubjectSectionEnrollment enrollment : enrollments) {
            students.add(toSnapshot(enrollment.getId(), enrollment.getStudent(), enrollment.getStatus(),
                    enrollment.getFinalGradeType(), grades, attendance));
        }

        return new SectionSnapshot(students, evaluationSchemeFinder.findCourseSectionScheme(sectionId).orElse(null));
    }

    private StudentSnapshot toSnapshot(Long enrollmentId,
                                       com.red_escuela.backend.identity.entity.Student student,
                                       com.red_escuela.backend.enrollment.entity.EnrollmentStatus status,
                                       com.red_escuela.backend.enrollment.entity.FinalGradeType finalGradeType,
                                       Map<Long, EnrollmentGradeScores> grades,
                                       Map<Long, long[]> attendance) {
        EnrollmentGradeScores scores = grades.getOrDefault(enrollmentId, EnrollmentGradeScores.empty());
        long[] counts = attendance.getOrDefault(enrollmentId, new long[]{0L, 0L});
        return new StudentSnapshot(
                student,
                status,
                finalGradeType,
                scores.examScores(),
                scores.tpScores(),
                scores.finalScores(),
                counts[1],
                counts[0]);
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
