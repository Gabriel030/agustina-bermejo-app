package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.api.AttendanceStanding;
import com.red_escuela.backend.attendance.api.AttendanceStandingReader;
import com.red_escuela.backend.attendance.dto.AttendanceStandingResponse;
import com.red_escuela.backend.attendance.dto.RosterEntryResponse;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class SectionRosterBuilder {

    private final AttendanceStandingReader attendanceStandingReader;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;

    public SectionRosterBuilder(AttendanceStandingReader attendanceStandingReader,
                                SectionEnrollmentFinder sectionEnrollmentFinder) {
        this.attendanceStandingReader = attendanceStandingReader;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
    }

    public List<RosterEntryResponse> buildForProgramSection(Long sectionId) {
        List<SubjectSectionEnrollment> enrollments = sectionEnrollmentFinder.findActiveWithStudentInProgramSection(sectionId);
        if (enrollments.isEmpty()) {
            return List.of();
        }
        Percentage max = enrollments.get(0).getSubjectSection().getMaxAbsenceRate();
        Map<Long, AttendanceStanding> standings = attendanceStandingReader.readProgramSectionStandings(sectionId, max);
        return enrollments.stream()
                .map(enrollment -> buildEntry(enrollment.getId(), enrollment.getStudent(),
                        standings.getOrDefault(enrollment.getId(), attendanceStandingReader.emptyStanding(max))))
                .toList();
    }

    public List<RosterEntryResponse> buildForCourseSection(Long sectionId) {
        List<CourseSubjectSectionEnrollment> enrollments = sectionEnrollmentFinder.findActiveWithStudentInCourseSection(sectionId);
        if (enrollments.isEmpty()) {
            return List.of();
        }
        Percentage max = enrollments.get(0).getCourseSubjectSection().getMaxAbsenceRate();
        Map<Long, AttendanceStanding> standings = attendanceStandingReader.readCourseSectionStandings(sectionId, max);
        return enrollments.stream()
                .map(enrollment -> buildEntry(enrollment.getId(), enrollment.getStudent(),
                        standings.getOrDefault(enrollment.getId(), attendanceStandingReader.emptyStanding(max))))
                .toList();
    }

    public List<RosterEntryResponse> sortByAbsences(List<RosterEntryResponse> roster, Integer minAbsences) {
        return roster.stream()
                .filter(r -> minAbsences == null || r.absenceCount() >= minAbsences)
                .sorted(Comparator.comparingLong(RosterEntryResponse::absenceCount).reversed())
                .toList();
    }

    private RosterEntryResponse buildEntry(Long enrollmentId, Student student, AttendanceStanding standing) {
        Person person = student.getPerson();
        return new RosterEntryResponse(
                enrollmentId, student.getId(), fullName(person), person.getDni(),
                standing.countableAbsences(), standing.presencePercent(), standing.exceedsAbsenceLimit(),
                standing.classesHeld(), standing.maxAbsencePercent(), standing.remainingAbsences(),
                standing.absencePercent(), standing.pendingJustificationAbsences(),
                AttendanceStandingResponse.from(standing));
    }

    private String fullName(Person person) {
        return person.getName() + " " + person.getLastname();
    }
}
