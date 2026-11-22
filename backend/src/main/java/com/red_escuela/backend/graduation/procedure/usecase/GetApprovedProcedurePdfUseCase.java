package com.red_escuela.backend.graduation.procedure.usecase;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;

public interface GetApprovedProcedurePdfUseCase {

    ProcedureDocumentPdf pdfOf(CertificateDocumentType documentType, Long id);
}
