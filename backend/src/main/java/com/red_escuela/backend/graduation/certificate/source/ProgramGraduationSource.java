package com.red_escuela.backend.graduation.certificate.source;

import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateNotEligibleException;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import org.springframework.stereotype.Component;

@Component
public class ProgramGraduationSource implements GraduationSource {

    private final ProgramEnrollmentFinder programEnrollmentFinder;
    private final GraduationDebtCheck graduationDebtCheck;
    private final GraduationSnapshotFactory snapshotFactory;

    public ProgramGraduationSource(ProgramEnrollmentFinder programEnrollmentFinder, GraduationDebtCheck graduationDebtCheck,
                                   GraduationSnapshotFactory snapshotFactory) {
        this.programEnrollmentFinder = programEnrollmentFinder;
        this.graduationDebtCheck = graduationDebtCheck;
        this.snapshotFactory = snapshotFactory;
    }

    @Override
    public GraduationCertificateKind kind() {
        return GraduationCertificateKind.PROGRAM;
    }

    @Override
    public void requireEligible(Long enrollmentId) {
        ProgramEnrollment enrollment = programEnrollmentFinder.requireById(enrollmentId);
        if (enrollment.getStatus() != ProgramEnrollmentStatus.COMPLETED || enrollment.getCompletionDate() == null) {
            throw new GraduationCertificateNotEligibleException("la inscripción a la carrera no está completada");
        }
        if (graduationDebtCheck.hasDebt(enrollment.getStudent().getId())) {
            throw new GraduationCertificateNotEligibleException("el alumno tiene deuda pendiente");
        }
    }

    @Override
    public CertificateSnapshot snapshotOf(Long enrollmentId) {
        ProgramEnrollment enrollment = programEnrollmentFinder.requireById(enrollmentId);
        return snapshotFactory.create(enrollment.getStudent(), "la carrera", enrollment.getProgram().getName(),
                enrollment.getProgram().getLevel() == null ? null : enrollment.getProgram().getLevel().name(),
                enrollment.getProgram().getTotalHoursRequired(), enrollment.getCompletionDate().toLocalDate());
    }

    @Override
    public Long studentIdOf(Long enrollmentId) {
        return programEnrollmentFinder.requireById(enrollmentId).getStudent().getId();
    }

    @Override
    public Long titleIdOf(Long enrollmentId) {
        return programEnrollmentFinder.requireById(enrollmentId).getProgram().getId();
    }
}
