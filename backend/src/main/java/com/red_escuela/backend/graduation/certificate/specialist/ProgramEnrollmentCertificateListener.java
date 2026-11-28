package com.red_escuela.backend.graduation.certificate.specialist;

import com.red_escuela.backend.enrollment.events.ProgramEnrollmentWithdrawnEvent;
import com.red_escuela.backend.evaluation.events.ProgramGradeCorrectedEvent;
import com.red_escuela.backend.evaluation.events.SubjectApprovalStandingChangedEvent;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist.ApprovedSubjectsEnrollmentCertificateRevoker;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularEnrollmentCertificateRevoker;
import com.red_escuela.backend.graduation.specialist.GraduationRequestEnrollmentCloser;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ProgramEnrollmentCertificateListener {

    static final String WITHDRAWN_REASON = "La matrícula de la carrera fue dada de baja";
    static final String APPROVAL_LOST_REASON = "Una materia dejó de estar aprobada después de emitida la constancia";
    static final String GRADE_CORRECTED_REASON = "Se corrigió una nota después de emitida la constancia";

    private final RegularEnrollmentCertificateRevoker regularRevoker;
    private final ApprovedSubjectsEnrollmentCertificateRevoker approvedSubjectsRevoker;
    private final GraduationRequestEnrollmentCloser graduationRequestCloser;

    public ProgramEnrollmentCertificateListener(RegularEnrollmentCertificateRevoker regularRevoker,
                                                ApprovedSubjectsEnrollmentCertificateRevoker approvedSubjectsRevoker,
                                                GraduationRequestEnrollmentCloser graduationRequestCloser) {
        this.regularRevoker = regularRevoker;
        this.approvedSubjectsRevoker = approvedSubjectsRevoker;
        this.graduationRequestCloser = graduationRequestCloser;
    }

    @EventListener
    public void onProgramEnrollmentWithdrawn(ProgramEnrollmentWithdrawnEvent event) {
        regularRevoker.revokeValidCertificate(RegularityKind.PROGRAM, event.programEnrollmentId(), WITHDRAWN_REASON);
        approvedSubjectsRevoker.revokeValidCertificates(event.programEnrollmentId(), WITHDRAWN_REASON);
        graduationRequestCloser.closeOpenRequests(event.programEnrollmentId(), WITHDRAWN_REASON);
    }

    @EventListener
    public void onSubjectApprovalStandingChanged(SubjectApprovalStandingChangedEvent event) {
        if (!event.approved()) {
            approvedSubjectsRevoker.revokeValidCertificates(event.programEnrollmentId(), APPROVAL_LOST_REASON);
        }
    }

    @EventListener
    public void onProgramGradeCorrected(ProgramGradeCorrectedEvent event) {
        approvedSubjectsRevoker.revokeValidCertificates(event.programEnrollmentId(), GRADE_CORRECTED_REASON);
    }
}
