package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.CurriculumProgressView;
import com.red_escuela.backend.enrollment.policy.GraduationProjection;
import com.red_escuela.backend.enrollment.policy.GraduationProjectionInput.ProjectionElectiveGroup;
import com.red_escuela.backend.enrollment.policy.GraduationProjectionInput.ProjectionSubject;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.policy.GraduationProjectionInput;
import com.red_escuela.backend.enrollment.policy.GraduationProjectionPolicy;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
@Transactional(readOnly = true)
public class GraduationProjectionReader {

    private static final List<EnrollmentStatus> ATTEMPT_STATUSES = List.of(EnrollmentStatus.ENROLLED,
            EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR, EnrollmentStatus.APPROVED, EnrollmentStatus.FAILED);

    private final GraduationProjectionPolicy projectionPolicy = new GraduationProjectionPolicy();
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final CurriculumProgressReader curriculumProgressReader;
    private final Clock clock;

    public GraduationProjectionReader(ProgramEnrollmentRepository programEnrollmentRepository,
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            CurriculumProgressReader curriculumProgressReader, Clock clock) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.curriculumProgressReader = curriculumProgressReader;
        this.clock = clock;
    }

    public GraduationProjection readOwnProjection(Long studentId, Long programEnrollmentId) {
        ProgramEnrollment enrollment = findEnrollment(programEnrollmentId);
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new ProgramEnrollmentNotFoundException(programEnrollmentId);
        }
        return projectionOf(enrollment);
    }

    public GraduationProjection readProjection(Long programEnrollmentId) {
        return projectionOf(findEnrollment(programEnrollmentId));
    }

    private ProgramEnrollment findEnrollment(Long programEnrollmentId) {
        return programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
    }

    private GraduationProjection projectionOf(ProgramEnrollment enrollment) {
        LocalDate today = LocalDate.now(clock);
        LocalDate firstCourseStart = subjectSectionEnrollmentRepository
                .findFirstTermStartOfProgramEnrollment(enrollment.getId(), ATTEMPT_STATUSES)
                .map(start -> start.toLocalDate()).orElse(null);
        CurriculumProgressView progress = curriculumProgressReader.readProgress(enrollment.getId());
        CurriculumProgressView.Summary summary = progress.summary();
        return projectionPolicy.project(new GraduationProjectionInput(enrollment.getId(), enrollment.getStatus(),
                enrollment.getProgram().getTotalYears(), projectionPolicy.closedPeriodsBetween(firstCourseStart, today),
                today, summary.accumulatedCredits(), summary.requiredCredits(), summary.remainingCredits(),
                summary.mandatoryTotal(), summary.mandatoryApproved(), summary.counts().approved(),
                progress.warnings().stream().anyMatch(warning -> "PLAN_CHANGED_AFTER_ENROLLMENT".equals(warning.code())),
                progress.subjects().stream().anyMatch(subject -> !subject.corequisites().isEmpty()),
                progress.subjects().stream().map(this::projectionSubjectOf).toList(),
                summary.electiveGroups().stream().map(group -> new ProjectionElectiveGroup(group.electiveGroupId(),
                        group.minRequired(), group.maxAllowed(), group.fulfilled())).toList(),
                summary.accumulatedCreditsByCoursework()));
    }

    private ProjectionSubject projectionSubjectOf(CurriculumProgressView.SubjectView subject) {
        return new ProjectionSubject(subject.subjectId(), subject.credits(), subject.curricularYear(),
                subject.curricularTerm(), subject.electiveGroupId(), subject.state(),
                subject.blockedBy().stream().map(CurriculumProgressView.BlockingPrerequisite::subjectId).toList());
    }
}
