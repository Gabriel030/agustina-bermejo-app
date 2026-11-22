package com.red_escuela.backend.graduation.procedure.controller;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.procedure.dto.ApprovedProcedureResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureEligibilityResponse;
import com.red_escuela.backend.graduation.procedure.dto.StudentProcedureType;
import com.red_escuela.backend.graduation.procedure.usecase.GetApprovedProcedurePdfUseCase;
import com.red_escuela.backend.graduation.procedure.usecase.GetProcedureEligibilityUseCase;
import com.red_escuela.backend.graduation.procedure.usecase.ListApprovedProceduresUseCase;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/me/procedures")
public class MeProcedureController {

    private final GetProcedureEligibilityUseCase eligibilityUseCase;
    private final ListApprovedProceduresUseCase approvedUseCase;
    private final GetApprovedProcedurePdfUseCase pdfUseCase;

    public MeProcedureController(GetProcedureEligibilityUseCase eligibilityUseCase,
                                 ListApprovedProceduresUseCase approvedUseCase,
                                 GetApprovedProcedurePdfUseCase pdfUseCase) {
        this.eligibilityUseCase = eligibilityUseCase;
        this.approvedUseCase = approvedUseCase;
        this.pdfUseCase = pdfUseCase;
    }

    @GetMapping("/{type}/eligibility")
    public ProcedureEligibilityResponse eligibility(@PathVariable StudentProcedureType type,
                                                    @RequestParam(required = false) Long enrollmentId,
                                                    @RequestParam(required = false) Long registrationId) {
        return eligibilityUseCase.eligibilityOf(type, enrollmentId, registrationId);
    }

    @GetMapping("/approved")
    public List<ApprovedProcedureResponse> approved() {
        return approvedUseCase.listApproved();
    }

    @GetMapping("/approved/{documentType}/{id}/pdf")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> pdf(@PathVariable CertificateDocumentType documentType, @PathVariable Long id) {
        ProcedureDocumentPdf pdf = pdfUseCase.pdfOf(documentType, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("constancia-" + pdf.code() + ".pdf").build().toString())
                .body(pdf.content());
    }
}
