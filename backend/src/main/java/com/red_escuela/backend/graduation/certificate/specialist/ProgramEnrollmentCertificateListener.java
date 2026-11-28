package com.red_escuela.backend.graduation.certificate.specialist;

import com.red_escuela.backend.enrollment.events.ProgramEnrollmentWithdrawnEvent;
import com.red_escuela.backend.enrollment.events.ProgramSectionStatusChangedEvent;
import com.red_escuela.backend.enrollment.events.SubjectEquivalenceVoidedEvent;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist.ApprovedSubjectsEnrollmentCertificateRevoker;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularEnrollmentCertificateRevoker;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularityLossRevoker;
import com.red_escuela.backend.graduation.specialist.GraduationRequestEnrollmentCloser;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ProgramEnrollmentCertificateListener {

    static final String WITHDRAWN_REASON = "La matrícula de la carrera fue dada de baja";
    static final String EQUIVALENCE_VOIDED_REASON = "Se anuló una equivalencia después de emitida la constancia";
    static final String APPROVAL_CHANGED_REASON = "La aprobación de una materia cambió después de emitida la constancia";

    private final RegularEnrollmentCertificateRevoker regularRevoker;
    private final ApprovedSubjectsEnrollmentCertificateRevoker approvedSubjectsRevoker;
    private final GraduationRequestEnrollmentCloser graduationRequestCloser;
    private final RegularityLossRevoker regularityLossRevoker;

    public ProgramEnrollmentCertificateListener(RegularEnrollmentCertificateRevoker regularRevoker,
                                                ApprovedSubjectsEnrollmentCertificateRevoker approvedSubjectsRevoker,
                                                GraduationRequestEnrollmentCloser graduationRequestCloser,
                                                RegularityLossRevoker regularityLossRevoker) {
        this.regularRevoker = regularRevoker;
        this.approvedSubjectsRevoker = approvedSubjectsRevoker;
        this.graduationRequestCloser = graduationRequestCloser;
        this.regularityLossRevoker = regularityLossRevoker;
    }

    @EventListener
    public void onProgramEnrollmentWithdrawn(ProgramEnrollmentWithdrawnEvent event) {
        regularRevoker.revokeValidCertificate(RegularityKind.PROGRAM, event.programEnrollmentId(), WITHDRAWN_REASON);
        approvedSubjectsRevoker.revokeValidCertificates(event.programEnrollmentId(), WITHDRAWN_REASON);
        graduationRequestCloser.closeOpenRequests(event.programEnrollmentId(), WITHDRAWN_REASON);
    }

    @EventListener
    public void onProgramSectionStatusChanged(ProgramSectionStatusChangedEvent event) {
        if (event.approvedOutcomeChanged()) {
            approvedSubjectsRevoker.revokeValidCertificates(event.programEnrollmentId(), APPROVAL_CHANGED_REASON);
        }
        if (event.statusChanged()) {
            regularityLossRevoker.revokeIfNoLongerRegular(RegularityKind.PROGRAM, event.programEnrollmentId());
        }
    }

    @EventListener
    public void onSubjectEquivalenceVoided(SubjectEquivalenceVoidedEvent event) {
        approvedSubjectsRevoker.revokeValidCertificates(event.programEnrollmentId(), EQUIVALENCE_VOIDED_REASON);
    }
}
