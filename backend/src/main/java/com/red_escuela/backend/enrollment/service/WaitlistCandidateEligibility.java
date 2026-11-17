package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.ProgramPrerequisiteEvaluator;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.dto.WaitlistVerdict;
import com.red_escuela.backend.enrollment.dto.WaitlistVerdictReason;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentApplicationRepository;
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

    public WaitlistCandidateEligibility(SubjectRetakeGuard subjectRetakeGuard,
                                        ProgramPrerequisiteEvaluator prerequisiteEvaluator,
                                        StudentScheduleOverlapGuard scheduleOverlapGuard,
                                        CourseEnrollmentApplicationRepository courseApplicationRepository,
                                        AdmissionInvoiceReceiptFinder admissionInvoiceReceiptFinder) {
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

    private Optional<WaitlistVerdictReason> firstViolation(WaitlistCandidate candidate) {
        if (candidate.kind() == WaitlistKind.PROGRAM_SECTION) {
            return programSectionViolation(candidate);
        }
        if (candidate.kind() == WaitlistKind.COURSE && hasReceiptPendingReview(candidate.enrollmentId())) {
            return Optional.of(WaitlistVerdictReason.ADMISSION_RECEIPT_PENDING);
        }
        return Optional.empty();
    }

    private Optional<WaitlistVerdictReason> programSectionViolation(WaitlistCandidate candidate) {
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

    private boolean hasReceiptPendingReview(Long courseEnrollmentId) {
        return courseApplicationRepository.findInvoiceIdsByCourseEnrollmentId(courseEnrollmentId).stream()
                .findFirst()
                .filter(admissionInvoiceReceiptFinder::isLatestReceiptPendingReview)
                .isPresent();
    }
}
