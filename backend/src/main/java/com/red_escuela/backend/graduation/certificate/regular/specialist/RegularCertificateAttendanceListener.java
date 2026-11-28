package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RegularCertificateAttendanceListener {

    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final RegularityLossRevoker regularityLossRevoker;

    public RegularCertificateAttendanceListener(SectionEnrollmentFinder sectionEnrollmentFinder,
                                                RegularityLossRevoker regularityLossRevoker) {
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.regularityLossRevoker = regularityLossRevoker;
    }

    @EventListener
    public void revokeCertificateWhenRegularityIsLost(AttendanceStatusChangedEvent event) {
        if (event.programSectionEnrollmentId() != null) {
            sectionEnrollmentFinder.findProgramEnrollmentIfPresent(event.programSectionEnrollmentId())
                    .ifPresent(section -> regularityLossRevoker.revokeIfNoLongerRegular(
                            RegularityKind.PROGRAM, section.getProgramEnrollment().getId()));
        }
        if (event.courseSectionEnrollmentId() != null) {
            regularityLossRevoker.revokeIfNoLongerRegular(RegularityKind.COURSE,
                    sectionEnrollmentFinder.findCourseEnrollment(event.courseSectionEnrollmentId()).getCourseEnrollment().getId());
        }
    }
}
