package com.red_escuela.backend.graduation.certificate.regular.service;

import com.red_escuela.backend.attendance.api.AttendanceStandingFinder;
import com.red_escuela.backend.attendance.api.AttendanceStandingView;
import com.red_escuela.backend.enrollment.api.ActiveSectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityReason;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityVerdict;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class ProgramRegularityRule implements RegularityRule {

    private final ProgramEnrollmentFinder programEnrollmentFinder;
    private final ActiveSectionEnrollmentFinder activeSectionEnrollmentFinder;
    private final AttendanceStandingFinder attendanceStandingFinder;
    private final Clock clock;

    public ProgramRegularityRule(ProgramEnrollmentFinder programEnrollmentFinder,
                                 ActiveSectionEnrollmentFinder activeSectionEnrollmentFinder,
                                 AttendanceStandingFinder attendanceStandingFinder,
                                 Clock clock) {
        this.programEnrollmentFinder = programEnrollmentFinder;
        this.activeSectionEnrollmentFinder = activeSectionEnrollmentFinder;
        this.attendanceStandingFinder = attendanceStandingFinder;
        this.clock = clock;
    }

    @Override
    public RegularityKind kind() {
        return RegularityKind.PROGRAM;
    }

    @Override
    public RegularityVerdict evaluate(Long enrollmentId) {
        Instant evaluatedAt = Instant.now(clock);
        if (programEnrollmentFinder.requireById(enrollmentId).getStatus() != ProgramEnrollmentStatus.ENROLLED) {
            return rejected(RegularityReason.PROGRAM_NOT_ENROLLED, false, evaluatedAt);
        }
        List<Long> sectionEnrollmentIds = activeSectionEnrollmentFinder.activeProgramSectionEnrollmentIds(enrollmentId);
        if (sectionEnrollmentIds.isEmpty()) {
            return rejected(RegularityReason.NO_ACTIVE_SECTION, false, evaluatedAt);
        }
        List<AttendanceStandingView> standings = sectionEnrollmentIds.stream()
                .map(attendanceStandingFinder::ofProgramSectionEnrollment)
                .toList();
        AttendanceStandingSummary summary = AttendanceStandingSummary.of(standings);
        if (summary.anyFree()) {
            return rejected(RegularityReason.FREE_IN_ACTIVE_SECTION, summary.controlled(), evaluatedAt);
        }
        return new RegularityVerdict(RegularityKind.PROGRAM, true, List.of(), summary.controlled(), evaluatedAt);
    }

    private RegularityVerdict rejected(RegularityReason reason, boolean controlled, Instant evaluatedAt) {
        return new RegularityVerdict(RegularityKind.PROGRAM, false, List.of(reason), controlled, evaluatedAt);
    }
}
