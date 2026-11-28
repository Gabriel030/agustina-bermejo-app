package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.ProgramPrerequisiteEvaluator;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.dto.WaitlistVerdict;
import com.red_escuela.backend.enrollment.dto.WaitlistVerdictReason;
import com.red_escuela.backend.enrollment.exception.SectionScheduleOverlapException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentApplicationRepository;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class WaitlistCandidateEligibility {

    private final SubjectRetakeGuard subjectRetakeGuard;
    private final ProgramPrerequisiteEvaluator prerequisiteEvaluator;
    private final StudentScheduleOverlapGuard scheduleOverlapGuard;
    private final CourseEnrollmentApplicationRepository courseApplicationRepository;
    private final AdmissionInvoiceReceiptFinder admissionInvoiceReceiptFinder;
    private final ProgramEnrollmentRepository programEnrollmentRepository;

    public WaitlistCandidateEligibility(SubjectRetakeGuard subjectRetakeGuard,
                                        ProgramPrerequisiteEvaluator prerequisiteEvaluator,
                                        StudentScheduleOverlapGuard scheduleOverlapGuard,
                                        CourseEnrollmentApplicationRepository courseApplicationRepository,
                                        AdmissionInvoiceReceiptFinder admissionInvoiceReceiptFinder,
                                        ProgramEnrollmentRepository programEnrollmentRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectRetakeGuard = subjectRetakeGuard;
        this.prerequisiteEvaluator = prerequisiteEvaluator;
        this.scheduleOverlapGuard = scheduleOverlapGuard;
        this.courseApplicationRepository = courseApplicationRepository;
        this.admissionInvoiceReceiptFinder = admissionInvoiceReceiptFinder;
    }

    public WaitlistVerdict evaluate(WaitlistCandidate candidate) {
        return firstViolation(candidate)
                .map(WaitlistVerdict::skippedBecause)
                .orElseGet(WaitlistVerdict::passing);
    }

    public Optional<String> findConflictingSectionName(WaitlistCandidate candidate) {
        return scheduleOverlapGuard.findOverlapForProgramSection(candidate.studentId(), candidate.sectionId(),
                candidate.termId(), candidate.enrollmentId()).map(SectionScheduleOverlapException::conflictingSubjectName);
    }

    private Optional<WaitlistVerdictReason> firstViolation(WaitlistCandidate candidate) {
        if (candidate.kind() == WaitlistKind.PROGRAM_SECTION) {
            return programSectionViolation(candidate);
        }
        if (candidate.kind() == WaitlistKind.COURSE && lacksApprovedAdmissionReceipt(candidate.enrollmentId())) {
            return Optional.of(WaitlistVerdictReason.ADMISSION_RECEIPT_PENDING);
        }
        return Optional.empty();
    }

    private Optional<WaitlistVerdictReason> programSectionViolation(WaitlistCandidate candidate) {
        if (!hasActiveProgramEnrollment(candidate.programEnrollmentId())) {
            return Optional.of(WaitlistVerdictReason.PROGRAM_ENROLLMENT_NOT_ACTIVE);
        }
        if (subjectRetakeGuard.isAlreadyPassed(candidate.programEnrollmentId(), candidate.subjectId(), candidate.enrollmentId())) {
            return Optional.of(WaitlistVerdictReason.ALREADY_PASSED);
        }
        if (!prerequisiteEvaluator.areAllMetOrSubjectOutsidePlan(candidate.programEnrollmentId(), candidate.subjectId())) {
            return Optional.of(WaitlistVerdictReason.PREREQUISITES_NOT_MET);
        }
        if (scheduleOverlapGuard.findOverlapForProgramSection(candidate.studentId(), candidate.sectionId(),
                candidate.termId(), candidate.enrollmentId()).isPresent()) {
            return Optional.of(WaitlistVerdictReason.SCHEDULE_OVERLAP);
        }
        return Optional.empty();
    }

    private boolean hasActiveProgramEnrollment(Long programEnrollmentId) {
        return programEnrollmentRepository.findById(programEnrollmentId)
                .map(enrollment -> enrollment.getStatus() == ProgramEnrollmentStatus.ENROLLED)
                .orElse(false);
    }

    private boolean lacksApprovedAdmissionReceipt(Long courseEnrollmentId) {
        return courseApplicationRepository.findInvoiceIdsByCourseEnrollmentId(courseEnrollmentId).stream()
                .findFirst()
                .map(admissionInvoiceReceiptFinder::isLatestReceiptApproved)
                .map(approved -> !approved)
                .orElse(true);
    }
}
