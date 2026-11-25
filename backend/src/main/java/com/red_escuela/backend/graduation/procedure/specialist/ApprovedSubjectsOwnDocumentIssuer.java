package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.RequestApprovedSubjectsCertificateRequest;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.usecase.RequestApprovedSubjectsCertificateUseCase;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.procedure.dto.GenerateOwnDocumentRequest;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentResponse;
import com.red_escuela.backend.graduation.procedure.exception.ProcedureNotEligibleException;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Component;

@Component
public class ApprovedSubjectsOwnDocumentIssuer {

    private final CurrentActorFinder currentActorFinder;
    private final OwnProgramEnrollmentResolver enrollmentResolver;
    private final RequestApprovedSubjectsCertificateUseCase requestUseCase;

    public ApprovedSubjectsOwnDocumentIssuer(CurrentActorFinder currentActorFinder, OwnProgramEnrollmentResolver enrollmentResolver,
                                             RequestApprovedSubjectsCertificateUseCase requestUseCase) {
        this.currentActorFinder = currentActorFinder;
        this.enrollmentResolver = enrollmentResolver;
        this.requestUseCase = requestUseCase;
    }

    public OwnDocumentResponse issue(GenerateOwnDocumentRequest request) {
        Long requestedEnrollmentId = request == null ? null : request.enrollmentId();
        Long enrollmentId = enrollmentResolver.resolve(currentActorFinder.currentStudentId(), requestedEnrollmentId)
                .enrollmentId()
                .orElseThrow(ProcedureNotEligibleException::new);
        ApprovedSubjectsCertificateResponse issued = requestUseCase.request(new RequestApprovedSubjectsCertificateRequest(enrollmentId));
        return new OwnDocumentResponse(CertificateDocumentType.APPROVED_SUBJECTS_CERTIFICATE, issued.id(), issued.code(),
                issued.status(), issued.issuedAt(), issued.expiresAt(), issued.verificationUrl());
    }
}
