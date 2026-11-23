package com.red_escuela.backend.graduation.procedure.usecase;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import com.red_escuela.backend.graduation.procedure.exception.ProcedureDocumentNotFoundException;
import com.red_escuela.backend.graduation.procedure.specialist.ApprovedProcedureSources;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetApprovedProcedurePdfService implements GetApprovedProcedurePdfUseCase {

    private final ApprovedProcedureSources sources;
    private final CurrentActorFinder currentActorFinder;

    public GetApprovedProcedurePdfService(ApprovedProcedureSources sources, CurrentActorFinder currentActorFinder) {
        this.sources = sources;
        this.currentActorFinder = currentActorFinder;
    }

    @Override
    public ProcedureDocumentPdf pdfOf(CertificateDocumentType documentType, Long id) {
        return sources.forType(documentType)
                .orElseThrow(ProcedureDocumentNotFoundException::new)
                .pdfOf(id, currentActorFinder.currentStudentId());
    }
}
