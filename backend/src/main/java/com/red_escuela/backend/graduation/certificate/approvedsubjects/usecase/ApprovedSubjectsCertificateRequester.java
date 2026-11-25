package com.red_escuela.backend.graduation.certificate.approvedsubjects.usecase;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.RequestApprovedSubjectsCertificateRequest;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.policy.ApprovedSubjectsBlocker;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.policy.ApprovedSubjectsEligibilityPolicy;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsActor;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsCertificateIssuing;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsEligibilityFacts;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsRefusals;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.OwnApprovedSubjectsEnrollments;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.OwnedEnrollment;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public class ApprovedSubjectsCertificateRequester {

    private final ApprovedSubjectsActor actor;
    private final OwnApprovedSubjectsEnrollments enrollments;
    private final ApprovedSubjectsEligibilityFacts eligibilityFacts;
    private final ApprovedSubjectsEligibilityPolicy eligibilityPolicy;
    private final ApprovedSubjectsCertificateIssuing issuing;
    private final ApprovedSubjectsRefusals refusals;
    private final Clock clock;

    public ApprovedSubjectsCertificateRequester(ApprovedSubjectsActor actor, OwnApprovedSubjectsEnrollments enrollments,
                                                     ApprovedSubjectsEligibilityFacts eligibilityFacts,
                                                     ApprovedSubjectsEligibilityPolicy eligibilityPolicy,
                                                     ApprovedSubjectsCertificateIssuing issuing, ApprovedSubjectsRefusals refusals,
                                                     Clock clock) {
        this.actor = actor;
        this.enrollments = enrollments;
        this.eligibilityFacts = eligibilityFacts;
        this.eligibilityPolicy = eligibilityPolicy;
        this.issuing = issuing;
        this.refusals = refusals;
        this.clock = clock;
    }

    public ApprovedSubjectsCertificateResponse request(RequestApprovedSubjectsCertificateRequest request) {
        Long studentId = actor.currentStudentId();
        OwnedEnrollment enrollment = enrollments.findOwnedBy(studentId, request.enrollmentId())
                .orElseThrow(refusals::enrollmentNotFound);
        LocalDateTime now = LocalDateTime.now(clock);
        List<ApprovedSubjectsBlocker> blockers = eligibilityPolicy.blockersOf(eligibilityFacts.inputFor(studentId, enrollment, now));
        rejectIfBlocked(blockers);
        return issuing.issue(studentId, enrollment.enrollmentId(), actor.currentPersonId(), now);
    }

    private void rejectIfBlocked(List<ApprovedSubjectsBlocker> blockers) {
        if (blockers.isEmpty()) {
            return;
        }
        boolean onlyDailyLimit = blockers.stream().allMatch(blocker -> blocker == ApprovedSubjectsBlocker.ISSUANCE_LIMIT_REACHED);
        if (onlyDailyLimit) {
            throw refusals.issuanceLimitReached();
        }
        throw refusals.notEligible();
    }
}
