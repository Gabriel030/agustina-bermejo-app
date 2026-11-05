package com.red_escuela.backend.graduation.certificate.controller;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateResponse;
import com.red_escuela.backend.graduation.certificate.dto.IssueGraduationCertificateRequest;
import com.red_escuela.backend.graduation.certificate.usecase.GetGraduationCertificatePdfUseCase;
import com.red_escuela.backend.graduation.certificate.usecase.IssueGraduationCertificateUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/graduation-certificates")
public class GraduationCertificateController {

    private final IssueGraduationCertificateUseCase issueUseCase;
    private final GetGraduationCertificatePdfUseCase pdfUseCase;

    public GraduationCertificateController(IssueGraduationCertificateUseCase issueUseCase,
                                           GetGraduationCertificatePdfUseCase pdfUseCase) {
        this.issueUseCase = issueUseCase;
        this.pdfUseCase = pdfUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public GraduationCertificateResponse issue(@Valid @RequestBody IssueGraduationCertificateRequest request) {
        return issueUseCase.issue(request);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        GraduationCertificatePdf pdf = pdfUseCase.pdfOf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("certificado-egreso-" + pdf.code() + ".pdf").build().toString())
                .body(pdf.content());
    }
}
