package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.ProgramApplicationDocumentFile;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationApprovalCheckResponse;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationPage;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.GetProgramEnrollmentApplicationApprovalCheckUseCase;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.GetProgramEnrollmentApplicationDocumentUseCase;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.GetProgramEnrollmentApplicationForReviewUseCase;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.ListPendingProgramEnrollmentApplicationsForReviewUseCase;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.ListResolvedProgramEnrollmentApplicationsUseCase;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ProgramEnrollmentApplicationReviewController {

    private final ListPendingProgramEnrollmentApplicationsForReviewUseCase listPendingUseCase;
    private final ListResolvedProgramEnrollmentApplicationsUseCase listResolvedUseCase;
    private final GetProgramEnrollmentApplicationForReviewUseCase getApplicationUseCase;
    private final GetProgramEnrollmentApplicationApprovalCheckUseCase approvalCheckUseCase;
    private final GetProgramEnrollmentApplicationDocumentUseCase documentUseCase;

    public ProgramEnrollmentApplicationReviewController(ListPendingProgramEnrollmentApplicationsForReviewUseCase listPendingUseCase,
                                                        ListResolvedProgramEnrollmentApplicationsUseCase listResolvedUseCase,
                                                        GetProgramEnrollmentApplicationForReviewUseCase getApplicationUseCase,
                                                        GetProgramEnrollmentApplicationApprovalCheckUseCase approvalCheckUseCase,
                                                        GetProgramEnrollmentApplicationDocumentUseCase documentUseCase) {
        this.listPendingUseCase = listPendingUseCase;
        this.listResolvedUseCase = listResolvedUseCase;
        this.getApplicationUseCase = getApplicationUseCase;
        this.approvalCheckUseCase = approvalCheckUseCase;
        this.documentUseCase = documentUseCase;
    }

    @GetMapping("/program-enrollment-applications/pending")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public List<ProgramEnrollmentApplicationResponse> pending() {
        return listPendingUseCase.list();
    }

    @GetMapping("/program-enrollment-applications/history")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ProgramEnrollmentApplicationPage history(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return listResolvedUseCase.list(status, search, page, size);
    }

    @GetMapping("/program-enrollment-applications/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ProgramEnrollmentApplicationResponse get(@PathVariable Long id) {
        return getApplicationUseCase.get(id);
    }

    @GetMapping("/program-enrollment-applications/{id}/approval-check")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ProgramEnrollmentApplicationApprovalCheckResponse approvalCheck(@PathVariable Long id) {
        return approvalCheckUseCase.get(id);
    }

    @GetMapping("/program-enrollment-applications/{id}/documents/{documentId}/file")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ResponseEntity<byte[]> documentFile(@PathVariable Long id, @PathVariable Long documentId) {
        ProgramApplicationDocumentFile file = documentUseCase.get(id, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.fileName()).build().toString())
                .body(file.content());
    }
}
