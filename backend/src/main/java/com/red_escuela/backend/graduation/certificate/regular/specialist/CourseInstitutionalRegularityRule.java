package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.attendance.api.AttendanceStandingFinder;
import com.red_escuela.backend.attendance.api.AttendanceStandingView;
import com.red_escuela.backend.enrollment.api.ActiveCourseSectionView;
import com.red_escuela.backend.enrollment.api.ActiveSectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.CourseEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityReason;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityVerdict;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
public class CourseInstitutionalRegularityRule implements InstitutionalRegularityRule {

    private static final Set<EnrollmentStatus> ACTIVE_STATUSES =
            Set.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR);

    private final CourseEnrollmentFinder courseEnrollmentFinder;
    private final ActiveSectionEnrollmentFinder activeSectionEnrollmentFinder;
    private final AttendanceStandingFinder attendanceStandingFinder;
    private final Clock clock;

    public CourseInstitutionalRegularityRule(CourseEnrollmentFinder courseEnrollmentFinder,
                                ActiveSectionEnrollmentFinder activeSectionEnrollmentFinder,
                                AttendanceStandingFinder attendanceStandingFinder,
                                Clock clock) {
        this.courseEnrollmentFinder = courseEnrollmentFinder;
        this.activeSectionEnrollmentFinder = activeSectionEnrollmentFinder;
        this.attendanceStandingFinder = attendanceStandingFinder;
        this.clock = clock;
    }

    @Override
    public RegularityKind kind() {
        return RegularityKind.COURSE;
    }

    @Override
    public RegularityVerdict evaluate(Long enrollmentId) {
        Instant evaluatedAt = Instant.now(clock);
        LocalDate today = LocalDate.now(clock);
        if (!ACTIVE_STATUSES.contains(courseEnrollmentFinder.requireById(enrollmentId).getStatus())) {
            return rejected(RegularityReason.NOT_ENROLLED, false, evaluatedAt);
        }
        List<ActiveCourseSectionView> sections = activeSectionEnrollmentFinder.activeCourseSections(enrollmentId);
        if (sections.isEmpty()) {
            return rejected(RegularityReason.NO_ACTIVE_SECTION, false, evaluatedAt);
        }
        List<ActiveCourseSectionView> openSections = sections.stream()
                .filter(section -> !section.termEndDate().isBefore(today))
                .toList();
        if (openSections.isEmpty()) {
            return rejected(RegularityReason.COURSE_FINISHED, false, evaluatedAt);
        }
        List<AttendanceStandingView> standings = openSections.stream()
                .map(section -> attendanceStandingFinder.ofCourseSectionEnrollment(section.sectionEnrollmentId()))
                .toList();
        AttendanceStandingSummary summary = AttendanceStandingSummary.of(standings);
        if (summary.anyFree()) {
            return rejected(RegularityReason.FREE_IN_ACTIVE_SECTION, summary.controlled(), evaluatedAt);
        }
        return new RegularityVerdict(RegularityKind.COURSE, true, List.of(), summary.controlled(), evaluatedAt);
    }

    private RegularityVerdict rejected(RegularityReason reason, boolean controlled, Instant evaluatedAt) {
        return new RegularityVerdict(RegularityKind.COURSE, false, List.of(reason), controlled, evaluatedAt);
    }
}
