package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.dto.RosterEntryResponse;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.repository.EnrollmentAttendanceCounts;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SectionRosterBuilder {

    private final AttendanceRepository attendanceRepository;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final AttendanceRiskCalculator riskCalculator;

    public SectionRosterBuilder(AttendanceRepository attendanceRepository,
                                 SectionEnrollmentFinder sectionEnrollmentFinder,
                                 AttendanceRiskCalculator riskCalculator) {
        this.attendanceRepository = attendanceRepository;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.riskCalculator = riskCalculator;
    }

    public List<RosterEntryResponse> buildForProgramSection(Long sectionId) {
        List<SubjectSectionEnrollment> enrollments = sectionEnrollmentFinder.findActiveWithStudentInProgramSection(sectionId);
        Map<Long, EnrollmentAttendanceCounts> counts = indexByEnrollment(
                attendanceRepository.countsByProgramSection(sectionId, AttendanceStatus.presenceStatuses(), AttendanceStatus.absenceStatuses()));
        return enrollments.stream()
                .map(enrollment -> buildEntry(enrollment.getId(), enrollment.getStudent(),
                        enrollment.getSubjectSection().getMaxAbsenceRate(), counts.get(enrollment.getId())))
                .toList();
    }

    public List<RosterEntryResponse> buildForCourseSection(Long sectionId) {
        List<CourseSubjectSectionEnrollment> enrollments = sectionEnrollmentFinder.findActiveWithStudentInCourseSection(sectionId);
        Map<Long, EnrollmentAttendanceCounts> counts = indexByEnrollment(
                attendanceRepository.countsByCourseSection(sectionId, AttendanceStatus.presenceStatuses(), AttendanceStatus.absenceStatuses()));
        return enrollments.stream()
                .map(enrollment -> buildEntry(enrollment.getId(), enrollment.getStudent(),
                        enrollment.getCourseSubjectSection().getMaxAbsenceRate(), counts.get(enrollment.getId())))
                .toList();
    }

    public List<RosterEntryResponse> sortByAbsences(List<RosterEntryResponse> roster, Integer minAbsences) {
        return roster.stream()
                .filter(r -> minAbsences == null || r.absenceCount() >= minAbsences)
                .sorted(Comparator.comparingLong(RosterEntryResponse::absenceCount).reversed())
                .toList();
    }

    private Map<Long, EnrollmentAttendanceCounts> indexByEnrollment(List<EnrollmentAttendanceCounts> counts) {
        return counts.stream().collect(Collectors.toMap(EnrollmentAttendanceCounts::getEnrollmentId, Function.identity()));
    }

    private RosterEntryResponse buildEntry(Long enrollmentId, Student student, Percentage maxAbsenceRate,
                                           EnrollmentAttendanceCounts counts) {
        Person person = student.getPerson();
        long total = counts == null ? 0 : counts.getTotal();
        long presentes = counts == null ? 0 : counts.getPresent();
        long absenceCount = counts == null ? 0 : counts.getAbsences();
        Percentage attendancePercent = Percentage.ratio(presentes, total).orElse(null);
        boolean atRisk = riskCalculator.evaluate(maxAbsenceRate, total, absenceCount).libre();
        return new RosterEntryResponse(
                enrollmentId, student.getId(), fullName(person), person.getDni(),
                absenceCount, attendancePercent, atRisk);
    }

    private String fullName(Person person) {
        return person.getName() + " " + person.getLastname();
    }
}
