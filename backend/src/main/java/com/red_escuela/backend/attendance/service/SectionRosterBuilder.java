package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.dto.RosterEntryResponse;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.repository.EnrollmentAttendanceCounts;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SectionRosterBuilder {

    private static final List<EnrollmentStatus> ACTIVE_ENROLLMENT_STATUSES =
            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS);
    private static final List<AttendanceStatus> ABSENCE_STATUSES_FOR_COUNT =
            List.of(AttendanceStatus.ABSENT, AttendanceStatus.JUSTIFICATION_PENDING);
    private static final Set<AttendanceStatus> PRESENT_LIKE_STATUSES =
            Set.of(AttendanceStatus.PRESENT, AttendanceStatus.LATE, AttendanceStatus.JUSTIFIED);
    private static final List<AttendanceStatus> PRESENT_LIKE_STATUSES_LIST = List.copyOf(PRESENT_LIKE_STATUSES);

    private final AttendanceRepository attendanceRepository;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository;
    private final AttendanceRiskCalculator riskCalculator;

    public SectionRosterBuilder(AttendanceRepository attendanceRepository,
                                 SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
                                 CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository,
                                 AttendanceRiskCalculator riskCalculator) {
        this.attendanceRepository = attendanceRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
        this.riskCalculator = riskCalculator;
    }

    public List<RosterEntryResponse> buildForProgramSection(Long sectionId) {
        List<SubjectSectionEnrollment> enrollments = subjectSectionEnrollmentRepository
                .findWithStudentBySectionIdAndStatusIn(sectionId, ACTIVE_ENROLLMENT_STATUSES);
        Map<Long, EnrollmentAttendanceCounts> counts = indexByEnrollment(
                attendanceRepository.countsByProgramSection(sectionId, PRESENT_LIKE_STATUSES_LIST, ABSENCE_STATUSES_FOR_COUNT));
        return enrollments.stream()
                .map(enrollment -> buildEntry(enrollment.getId(), enrollment.getStudent(),
                        enrollment.getSubjectSection().getMaxAbsencePercentage(), counts.get(enrollment.getId())))
                .toList();
    }

    public List<RosterEntryResponse> buildForCourseSection(Long sectionId) {
        List<CourseSubjectSectionEnrollment> enrollments = courseSubjectSectionEnrollmentRepository
                .findWithStudentBySectionIdAndStatusIn(sectionId, ACTIVE_ENROLLMENT_STATUSES);
        Map<Long, EnrollmentAttendanceCounts> counts = indexByEnrollment(
                attendanceRepository.countsByCourseSection(sectionId, PRESENT_LIKE_STATUSES_LIST, ABSENCE_STATUSES_FOR_COUNT));
        return enrollments.stream()
                .map(enrollment -> buildEntry(enrollment.getId(), enrollment.getStudent(),
                        enrollment.getCourseSubjectSection().getMaxAbsencePercentage(), counts.get(enrollment.getId())))
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

    private RosterEntryResponse buildEntry(Long enrollmentId, Student student, Double maxAbsencePercentage,
                                           EnrollmentAttendanceCounts counts) {
        Person person = student.getPerson();
        long total = counts == null ? 0 : counts.getTotal();
        long presentes = counts == null ? 0 : counts.getPresent();
        long absenceCount = counts == null ? 0 : counts.getAbsences();
        Double attendancePercentage = total == 0 ? null : (double) presentes / total;
        boolean atRisk = riskCalculator.evaluate(maxAbsencePercentage, total, absenceCount).libre();
        return new RosterEntryResponse(
                enrollmentId, student.getId(), fullName(person), person.getDni(),
                absenceCount, attendancePercentage, atRisk);
    }

    private String fullName(Person person) {
        return person.getName() + " " + person.getLastname();
    }
}
