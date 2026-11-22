package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
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

    public Optional<Long> resolve(Long studentId, Long enrollmentId) {
        if (enrollmentId != null) {
            try {
                return Optional.of(programEnrollmentFinder.requireOwnedBy(enrollmentId, studentId).getId());
            } catch (RuntimeException notOwned) {
                throw new ProcedureDocumentNotFoundException();
            }
        }
        List<ProgramEnrollment> active = programEnrollmentFinder.findByStudent(studentId).stream()
                .filter(enrollment -> enrollment.getStatus() == ProgramEnrollmentStatus.ENROLLED)
                .toList();
        return active.size() == 1 ? Optional.of(active.get(0).getId()) : Optional.empty();
    }
}
