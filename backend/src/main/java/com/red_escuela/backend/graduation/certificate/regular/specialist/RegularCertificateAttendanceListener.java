package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RegularCertificateAttendanceListener {

    static final String REGULARITY_LOST_REASON = "El alumno dejó de cumplir las condiciones de regularidad después de emitida la constancia";

    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final RegularityEvaluator regularityEvaluator;
    private final RegularEnrollmentCertificateRevoker revoker;

    public RegularCertificateAttendanceListener(SectionEnrollmentFinder sectionEnrollmentFinder, RegularityEvaluator regularityEvaluator,
                                                RegularEnrollmentCertificateRevoker revoker) {
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.regularityEvaluator = regularityEvaluator;
        this.revoker = revoker;
    }

    @EventListener
    public void revokeCertificateWhenRegularityIsLost(AttendanceStatusChangedEvent event) {
        if (event.programSectionEnrollmentId() != null) {
            sectionEnrollmentFinder.findProgramEnrollmentIfPresent(event.programSectionEnrollmentId())
                    .ifPresent(section -> revokeIfNoLongerRegular(RegularityKind.PROGRAM, section.getProgramEnrollment().getId()));
        }
        if (event.courseSectionEnrollmentId() != null) {
            revokeIfNoLongerRegular(RegularityKind.COURSE,
                    sectionEnrollmentFinder.findCourseEnrollment(event.courseSectionEnrollmentId()).getCourseEnrollment().getId());
        }
    }

    private void revokeIfNoLongerRegular(RegularityKind kind, Long enrollmentId) {
        if (revoker.hasValidCertificate(kind, enrollmentId)
                && !regularityEvaluator.evaluate(kind, enrollmentId).institutionallyRegular()) {
            revoker.revokeValidCertificate(kind, enrollmentId, REGULARITY_LOST_REASON);
        }
    }
}
