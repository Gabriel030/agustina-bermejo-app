package com.red_escuela.backend.graduation.certificate.source;

import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateBlockedByDebtException;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateNotEligibleException;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.graduation.entity.CertificateReviewStatus;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.exception.GraduationCertificateReviewRequiredException;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import org.springframework.stereotype.Component;

@Component
public class ProgramGraduationSource implements GraduationSource {

    private final ProgramEnrollmentFinder programEnrollmentFinder;
    private final GraduationDebtCheck graduationDebtCheck;
    private final GraduationSnapshotFactory snapshotFactory;
    private final GraduationRequestRepository graduationRequestRepository;

    public ProgramGraduationSource(ProgramEnrollmentFinder programEnrollmentFinder, GraduationDebtCheck graduationDebtCheck,
                                   GraduationSnapshotFactory snapshotFactory, GraduationRequestRepository graduationRequestRepository) {
        this.graduationRequestRepository = graduationRequestRepository;
        this.programEnrollmentFinder = programEnrollmentFinder;
        this.graduationDebtCheck = graduationDebtCheck;
        this.snapshotFactory = snapshotFactory;
    }

    @Override
    public GraduationCertificateKind kind() {
        return GraduationCertificateKind.PROGRAM;
    }

    @Override
    public void lockForIssuance(Long enrollmentId) {
        graduationRequestRepository.lockApprovedByProgramEnrollmentId(enrollmentId);
    }

    @Override
    public void requireEligible(Long enrollmentId) {
        ProgramEnrollment enrollment = programEnrollmentFinder.requireById(enrollmentId);
        if (enrollment.getStatus() != ProgramEnrollmentStatus.COMPLETED || enrollment.getCompletionDate() == null) {
            throw new GraduationCertificateNotEligibleException("la inscripción a la carrera no está completada");
        }
        requireCertificateReviewPassed(enrollmentId);
        if (graduationDebtCheck.hasDebt(enrollment.getStudent().getId())) {
            throw new GraduationCertificateBlockedByDebtException();
        }
    }

    private void requireCertificateReviewPassed(Long enrollmentId) {
        graduationRequestRepository.findFirstByProgramEnrollmentIdAndStatusOrderByIdDesc(enrollmentId, GraduationRequestStatus.APPROVED)
                .filter(request -> request.getCertificateReviewStatus() == CertificateReviewStatus.PENDING_REVIEW
                        || request.getCertificateReviewStatus() == CertificateReviewStatus.DISABLED)
                .ifPresent(request -> {
                    throw new GraduationCertificateReviewRequiredException(request.getId(), request.getCertificateReviewStatus().name());
                });
    }

    @Override
    public CertificateSnapshot snapshotOf(Long enrollmentId) {
        ProgramEnrollment enrollment = programEnrollmentFinder.requireById(enrollmentId);
        return snapshotFactory.create(enrollment.getStudent(), "la carrera", enrollment.getProgram().getName(),
                null, enrollment.getProgram().getTotalHoursRequired(), enrollment.getCompletionDate().toLocalDate());
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
