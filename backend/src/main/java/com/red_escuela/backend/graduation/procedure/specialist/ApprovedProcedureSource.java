package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.procedure.dto.ApprovedProcedureResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import java.util.List;

public interface ApprovedProcedureSource {

    CertificateDocumentType documentType();

    List<ApprovedProcedureResponse> listOf(Long studentId);

    ProcedureDocumentPdf pdfOf(Long id, Long studentId);
}
