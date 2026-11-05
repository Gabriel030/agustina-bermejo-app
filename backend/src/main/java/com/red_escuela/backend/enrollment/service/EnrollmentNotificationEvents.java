package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.EnrollmentFeeExemption;
import com.red_escuela.backend.enrollment.events.CourseWaitlistExpiredEvent;
import com.red_escuela.backend.enrollment.events.DirectorApprovalRequestApprovedEvent;
import com.red_escuela.backend.enrollment.events.DirectorApprovalRequestRejectedEvent;
import com.red_escuela.backend.enrollment.events.EnrollmentFeeExemptionGrantedEvent;
import com.red_escuela.backend.enrollment.events.EnrollmentFeeExemptionRevokedEvent;
import com.red_escuela.backend.identity.entity.Person;

public final class EnrollmentNotificationEvents {

    private EnrollmentNotificationEvents() {
    }

    public static CourseWaitlistExpiredEvent waitlistExpiredOf(CourseEnrollment enrollment) {
        return CourseWaitlistExpiredEvent.expiredFor(enrollment.getId(), enrollment.getStudent().getPerson().getId(), enrollment.getCourse().getName());
    }

    public static DirectorApprovalRequestApprovedEvent approvalOf(DirectorApprovalRequest request) {
        return DirectorApprovalRequestApprovedEvent.approvedFor(request.getId(), request.describe(), decisionNotesOf(request),
                request.student().getId(), requesterIdOf(request));
    }

    public static DirectorApprovalRequestRejectedEvent rejectionOf(DirectorApprovalRequest request) {
        return DirectorApprovalRequestRejectedEvent.rejectedFor(request.getId(), request.describe(), decisionNotesOf(request),
                request.student().getId(), requesterIdOf(request));
    }

    public static EnrollmentFeeExemptionGrantedEvent exemptionGrantOf(EnrollmentFeeExemption exemption) {
        return EnrollmentFeeExemptionGrantedEvent.grantedFor(exemption.getProgramEnrollment().getId(),
                exemption.getProgramEnrollment().getStudent().getPerson().getId());
    }

    public static EnrollmentFeeExemptionRevokedEvent exemptionRevocationOf(EnrollmentFeeExemption exemption) {
        return EnrollmentFeeExemptionRevokedEvent.revokedFor(exemption.getProgramEnrollment().getId(),
                exemption.getProgramEnrollment().getStudent().getPerson().getId());
    }

    private static String decisionNotesOf(DirectorApprovalRequest request) {
        return request.hasDecisionNotes() ? request.getDecisionNotes() : null;
    }

    private static Long requesterIdOf(DirectorApprovalRequest request) {
        Person requestedBy = request.getRequestedBy();
        return requestedBy == null ? null : requestedBy.getId();
    }
}
