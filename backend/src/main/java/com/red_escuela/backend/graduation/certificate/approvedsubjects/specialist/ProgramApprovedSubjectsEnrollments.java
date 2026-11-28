package com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist;

import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.OwnApprovedSubjectsEnrollments;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.OwnedEnrollment;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ProgramApprovedSubjectsEnrollments implements OwnApprovedSubjectsEnrollments {

    private final ProgramEnrollmentFinder programEnrollmentFinder;

    public ProgramApprovedSubjectsEnrollments(ProgramEnrollmentFinder programEnrollmentFinder) {
        this.programEnrollmentFinder = programEnrollmentFinder;
    }

    @Override
    public Optional<OwnedEnrollment> findOwnedBy(Long studentId, Long enrollmentId) {
        try {
            ProgramEnrollment enrollment = programEnrollmentFinder.requireOwnedBy(enrollmentId, studentId);
            return Optional.of(new OwnedEnrollment(enrollment.getId(), enrollment.getStatus() == ProgramEnrollmentStatus.ENROLLED));
        } catch (ProgramEnrollmentNotFoundException notOwned) {
            return Optional.empty();
        }
    }
}
