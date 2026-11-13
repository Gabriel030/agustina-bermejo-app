package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.repository.EnrollmentAttendanceCounts;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class AttendanceStandingReader {

    private final AttendanceRepository attendanceRepository;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;

    public AttendanceStandingReader(AttendanceRepository attendanceRepository,
                                    SectionEnrollmentFinder sectionEnrollmentFinder) {
        this.attendanceRepository = attendanceRepository;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
    }

    public AttendanceStanding readProgramEnrollmentStanding(Long enrollmentId) {
        SubjectSectionEnrollment enrollment = sectionEnrollmentFinder.findProgramEnrollment(enrollmentId);
        Percentage max = enrollment.getSubjectSection().getMaxAbsenceRate();
        return standingOf(max, indexByEnrollment(queryProgramEnrollments(List.of(enrollmentId))).get(enrollmentId));
    }

    public AttendanceStanding readCourseEnrollmentStanding(Long enrollmentId) {
        CourseSubjectSectionEnrollment enrollment = sectionEnrollmentFinder.findCourseEnrollment(enrollmentId);
        Percentage max = enrollment.getCourseSubjectSection().getMaxAbsenceRate();
        return standingOf(max, indexByEnrollment(queryCourseEnrollments(List.of(enrollmentId))).get(enrollmentId));
    }

    public Map<Long, AttendanceStanding> readProgramEnrollmentStandings(Collection<Long> enrollmentIds) {
        Map<Long, AttendanceStanding> standings = new HashMap<>();
        if (enrollmentIds.isEmpty()) {
            return standings;
        }
        Map<Long, EnrollmentAttendanceCounts> counts = indexByEnrollment(queryProgramEnrollments(enrollmentIds));
        for (SubjectSectionEnrollment enrollment : sectionEnrollmentFinder.findProgramEnrollmentsWithSection(enrollmentIds)) {
            standings.put(enrollment.getId(),
                    standingOf(enrollment.getSubjectSection().getMaxAbsenceRate(), counts.get(enrollment.getId())));
        }
        return standings;
    }

    public Map<Long, AttendanceStanding> readProgramSectionStandings(Long sectionId, Percentage maxAbsenceRate) {
        return standingsOf(maxAbsenceRate, queryProgramSection(sectionId));
    }

    public Map<Long, AttendanceStanding> readCourseSectionStandings(Long sectionId, Percentage maxAbsenceRate) {
        return standingsOf(maxAbsenceRate, queryCourseSection(sectionId));
    }

    public Map<Long, AttendanceStanding> readProgramEnrollmentCounts(Collection<Long> enrollmentIds) {
        return enrollmentIds.isEmpty() ? Map.of() : standingsOf(null, queryProgramEnrollments(enrollmentIds));
    }

    public Map<Long, AttendanceStanding> readCourseEnrollmentCounts(Collection<Long> enrollmentIds) {
        return enrollmentIds.isEmpty() ? Map.of() : standingsOf(null, queryCourseEnrollments(enrollmentIds));
    }

    public AttendanceStanding emptyStanding(Percentage maxAbsenceRate) {
        return standingOf(maxAbsenceRate, null);
    }

    private AttendanceStanding standingOf(Percentage maxAbsenceRate, EnrollmentAttendanceCounts counts) {
        long total = counts == null ? 0L : orZero(counts.getTotal());
        long present = counts == null ? 0L : orZero(counts.getPresent());
        long absences = counts == null ? 0L : orZero(counts.getAbsences());
        long pending = counts == null ? 0L : orZero(counts.getPending());
        return build(maxAbsenceRate, total, present, absences, pending);
    }

    private Map<Long, AttendanceStanding> standingsOf(Percentage max, List<EnrollmentAttendanceCounts> rows) {
        Map<Long, AttendanceStanding> standings = new HashMap<>();
        rows.forEach(row -> standings.put(row.getEnrollmentId(), standingOf(max, row)));
        return standings;
    }

    private AttendanceStanding build(Percentage max, long total, long present, long absences, long pending) {
        Optional<Percentage> absencePercent = Percentage.ratio(absences, total);
        Optional<Percentage> presencePercent = Percentage.ratio(present, total);
        boolean exceeds = max != null && absencePercent.map(percent -> percent.exceeds(max)).orElse(false);
        Integer allowed = max == null || total == 0 ? null : allowedAbsences(max, total);
        Integer remaining = allowed == null ? null : (int) Math.max(0, allowed - absences);
        return new AttendanceStanding(total, present, absences, pending, absencePercent.orElse(null),
                presencePercent.orElse(null), max, exceeds, allowed, remaining);
    }

    private int allowedAbsences(Percentage max, long total) {
        return max.fraction().multiply(BigDecimal.valueOf(total)).setScale(0, RoundingMode.FLOOR).intValue();
    }

    private List<EnrollmentAttendanceCounts> queryProgramEnrollments(Collection<Long> ids) {
        return attendanceRepository.countsByProgramEnrollmentIds(
                ids, AttendanceStatus.presenceStatuses(), AttendanceStatus.absenceStatuses(), AttendanceStatus.JUSTIFICATION_PENDING);
    }

    private List<EnrollmentAttendanceCounts> queryCourseEnrollments(Collection<Long> ids) {
        return attendanceRepository.countsByCourseEnrollmentIds(
                ids, AttendanceStatus.presenceStatuses(), AttendanceStatus.absenceStatuses(), AttendanceStatus.JUSTIFICATION_PENDING);
    }

    private List<EnrollmentAttendanceCounts> queryProgramSection(Long sectionId) {
        return attendanceRepository.countsByProgramSection(
                sectionId, AttendanceStatus.presenceStatuses(), AttendanceStatus.absenceStatuses(), AttendanceStatus.JUSTIFICATION_PENDING);
    }

    private List<EnrollmentAttendanceCounts> queryCourseSection(Long sectionId) {
        return attendanceRepository.countsByCourseSection(
                sectionId, AttendanceStatus.presenceStatuses(), AttendanceStatus.absenceStatuses(), AttendanceStatus.JUSTIFICATION_PENDING);
    }

    private Map<Long, EnrollmentAttendanceCounts> indexByEnrollment(List<EnrollmentAttendanceCounts> rows) {
        Map<Long, EnrollmentAttendanceCounts> byEnrollment = new HashMap<>();
        rows.forEach(row -> byEnrollment.put(row.getEnrollmentId(), row));
        return byEnrollment;
    }

    private long orZero(Long value) {
        return value == null ? 0L : value;
    }
}
