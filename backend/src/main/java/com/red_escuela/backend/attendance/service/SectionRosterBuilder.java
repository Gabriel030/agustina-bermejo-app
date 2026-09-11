package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.dto.RosterEntryResponse;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class SectionRosterBuilder {

    private static final List<EnrollmentStatus> ACTIVE_ENROLLMENT_STATUSES =
            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS);
    private static final List<AttendanceStatus> ABSENCE_STATUSES_FOR_COUNT =
            List.of(AttendanceStatus.ABSENT, AttendanceStatus.JUSTIFICATION_PENDING, AttendanceStatus.JUSTIFIED);
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
        List<SubjectSectionEnrollment> inscriptos =
                subjectSectionEnrollmentRepository.findBySubjectSectionIdAndStatusIn(sectionId, ACTIVE_ENROLLMENT_STATUSES);
        return inscriptos.stream().map(this::buildProgramEntry).toList();
    }

    public List<RosterEntryResponse> buildForCourseSection(Long sectionId) {
        List<CourseSubjectSectionEnrollment> inscriptos =
                courseSubjectSectionEnrollmentRepository.findByCourseSubjectSectionIdAndStatusIn(sectionId, ACTIVE_ENROLLMENT_STATUSES);
        return inscriptos.stream().map(this::buildCourseEntry).toList();
    }

    public List<RosterEntryResponse> sortByAbsences(List<RosterEntryResponse> roster, Integer minAbsences) {
        return roster.stream()
                .filter(r -> minAbsences == null || r.absenceCount() >= minAbsences)
                .sorted(Comparator.comparingLong(RosterEntryResponse::absenceCount).reversed())
                .toList();
    }

    private RosterEntryResponse buildProgramEntry(SubjectSectionEnrollment enrollment) {
        Person person = enrollment.getStudent().getPerson();
        long total = attendanceRepository.countByProgramSubjectSectionEnrollmentId(enrollment.getId());
        long presentes = attendanceRepository.countByProgramSubjectSectionEnrollmentIdAndStatusIn(
                enrollment.getId(), PRESENT_LIKE_STATUSES_LIST);
        long absenceCount = attendanceRepository.countByProgramSubjectSectionEnrollmentIdAndStatusIn(
                enrollment.getId(), ABSENCE_STATUSES_FOR_COUNT);
        Double attendancePercentage = total == 0 ? null : (double) presentes / total;
        boolean atRisk = riskCalculator.calculateForProgramEnrollment(enrollment.getId()).libre();
        return new RosterEntryResponse(
                enrollment.getId(), enrollment.getStudent().getId(), fullName(person), person.getDni(),
                absenceCount, attendancePercentage, atRisk);
    }

    private RosterEntryResponse buildCourseEntry(CourseSubjectSectionEnrollment enrollment) {
        Person person = enrollment.getStudent().getPerson();
        long total = attendanceRepository.countByCourseSubjectSectionEnrollmentId(enrollment.getId());
        long presentes = attendanceRepository.countByCourseSubjectSectionEnrollmentIdAndStatusIn(
                enrollment.getId(), PRESENT_LIKE_STATUSES_LIST);
        long absenceCount = attendanceRepository.countByCourseSubjectSectionEnrollmentIdAndStatusIn(
                enrollment.getId(), ABSENCE_STATUSES_FOR_COUNT);
        Double attendancePercentage = total == 0 ? null : (double) presentes / total;
        boolean atRisk = riskCalculator.calculateForCourseEnrollment(enrollment.getId()).libre();
        return new RosterEntryResponse(
                enrollment.getId(), enrollment.getStudent().getId(), fullName(person), person.getDni(),
                absenceCount, attendancePercentage, atRisk);
    }

    private String fullName(Person person) {
        return person.getName() + " " + person.getLastname();
    }
}
