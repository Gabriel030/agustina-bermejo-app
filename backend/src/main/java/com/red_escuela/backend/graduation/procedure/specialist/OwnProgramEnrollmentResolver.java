package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.graduation.procedure.exception.ProcedureDocumentNotFoundException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class OwnProgramEnrollmentResolver {

    private final ProgramEnrollmentFinder programEnrollmentFinder;

    public OwnProgramEnrollmentResolver(ProgramEnrollmentFinder programEnrollmentFinder) {
        this.programEnrollmentFinder = programEnrollmentFinder;
    }

    public Resolution resolve(Long studentId, Long enrollmentId) {
        if (enrollmentId != null) {
            return resolveExplicit(studentId, enrollmentId);
        }
        List<ProgramEnrollment> active = programEnrollmentFinder.findByStudent(studentId).stream()
                .filter(enrollment -> enrollment.getStatus() == ProgramEnrollmentStatus.ENROLLED)
                .toList();
        if (active.size() == 1) {
            return new Resolution(Optional.of(active.get(0).getId()), false);
        }
        return new Resolution(Optional.empty(), active.size() > 1);
    }

    private Resolution resolveExplicit(Long studentId, Long enrollmentId) {
        try {
            ProgramEnrollment enrollment = programEnrollmentFinder.requireOwnedBy(enrollmentId, studentId);
            boolean enrolled = enrollment.getStatus() == ProgramEnrollmentStatus.ENROLLED;
            return new Resolution(enrolled ? Optional.of(enrollment.getId()) : Optional.empty(), false);
        } catch (ProgramEnrollmentNotFoundException notOwned) {
            throw new ProcedureDocumentNotFoundException();
        }
    }

    public record Resolution(Optional<Long> enrollmentId, boolean ambiguous) {
    }
}
