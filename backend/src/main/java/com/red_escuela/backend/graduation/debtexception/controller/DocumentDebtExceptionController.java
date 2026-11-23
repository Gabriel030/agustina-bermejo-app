package com.red_escuela.backend.graduation.debtexception.controller;

import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionResponse;
import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus;
import com.red_escuela.backend.graduation.debtexception.dto.IssueDebtExceptionRequest;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import com.red_escuela.backend.graduation.debtexception.usecase.GetDebtExceptionPdfUseCase;
import com.red_escuela.backend.graduation.debtexception.usecase.IssueDebtExceptionUseCase;
import com.red_escuela.backend.graduation.debtexception.usecase.ListDebtExceptionsUseCase;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/document-debt-exceptions")
public class DocumentDebtExceptionController {

    private final IssueDebtExceptionUseCase issueUseCase;
    private final ListDebtExceptionsUseCase listUseCase;
    private final GetDebtExceptionPdfUseCase pdfUseCase;

    public DocumentDebtExceptionController(IssueDebtExceptionUseCase issueUseCase,
                                           ListDebtExceptionsUseCase listUseCase,
                                           GetDebtExceptionPdfUseCase pdfUseCase) {
        this.issueUseCase = issueUseCase;
        this.listUseCase = listUseCase;
        this.pdfUseCase = pdfUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "document.debt-exception.issue", legacyRoles = "ROLE_DIRECTOR,ROLE_ADMINISTRATIVO")
    public DebtExceptionResponse issue(@Valid @RequestBody IssueDebtExceptionRequest request) {
        return issueUseCase.issue(request);
    }

    @GetMapping
    @RequiresPermission(code = "document.debt-exception.issue", legacyRoles = "ROLE_DIRECTOR,ROLE_ADMINISTRATIVO")
    public Page<DebtExceptionResponse> list(@RequestParam(required = false) DebtExceptionStatus status,
                                            @RequestParam(required = false) Long studentId,
                                            @PageableDefault(size = 50) Pageable pageable) {
        return listUseCase.list(status, studentId, pageable);
    }

    @GetMapping("/{id}/pdf")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")))
    @RequiresPermission(code = "document.debt-exception.issue", legacyRoles = "ROLE_DIRECTOR,ROLE_ADMINISTRATIVO")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        ProcedureDocumentPdf pdf = pdfUseCase.pdfOf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("constancia-" + pdf.code() + ".pdf").build().toString())
                .body(pdf.content());
    }
}
