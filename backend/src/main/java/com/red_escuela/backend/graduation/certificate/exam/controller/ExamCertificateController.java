package com.red_escuela.backend.graduation.certificate.exam.controller;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateResponse;
import com.red_escuela.backend.graduation.certificate.exam.dto.RevokeExamCertificateRequest;
import com.red_escuela.backend.graduation.certificate.exam.usecase.GetExamCertificatePdfUseCase;
import com.red_escuela.backend.graduation.certificate.exam.usecase.RevokeExamCertificateUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExamCertificateController {

    private final RevokeExamCertificateUseCase revokeUseCase;

    private final GetExamCertificatePdfUseCase pdfUseCase;

    public ExamCertificateController(RevokeExamCertificateUseCase revokeUseCase, GetExamCertificatePdfUseCase pdfUseCase) {
        this.revokeUseCase = revokeUseCase;
        this.pdfUseCase = pdfUseCase;
    }

    @GetMapping("/exam-certificates/{id}/pdf")
    @RequiresPermission(code = "certificate.exam_download", legacyRoles = "ROLE_DOCENTE,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    @Operation(description = "Descarga individual. Docente de la mesa (404 si es ajena), Administrativo y Director. Audita DOCUMENT_PDF_DOWNLOADED.")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        GraduationCertificatePdf pdf = pdfUseCase.pdfOf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("constancia-examen-" + pdf.code() + ".pdf").build().toString())
                .body(pdf.content());
    }

    @PostMapping("/exam-certificates/{id}/revoke")
    @RequiresPermission(code = "certificate.revoke", legacyRoles = "ROLE_DIRECTOR")
    public ExamCertificateResponse revoke(@PathVariable Long id, @Valid @RequestBody RevokeExamCertificateRequest request) {
        return revokeUseCase.revoke(id, request.reason());
    }
}
