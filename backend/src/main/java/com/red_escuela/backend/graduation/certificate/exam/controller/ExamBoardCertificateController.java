package com.red_escuela.backend.graduation.certificate.exam.controller;

import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateBatchResponse;
import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateCandidateResponse;
import com.red_escuela.backend.graduation.certificate.exam.dto.IssueExamCertificateBatchRequest;
import com.red_escuela.backend.graduation.certificate.exam.usecase.IssueExamCertificateBatchUseCase;
import com.red_escuela.backend.graduation.certificate.exam.usecase.ListExamCertificateCandidatesUseCase;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/exam-boards/{id}")
public class ExamBoardCertificateController {

    private final ListExamCertificateCandidatesUseCase candidatesUseCase;
    private final IssueExamCertificateBatchUseCase issueBatchUseCase;

    public ExamBoardCertificateController(ListExamCertificateCandidatesUseCase candidatesUseCase,
                                          IssueExamCertificateBatchUseCase issueBatchUseCase) {
        this.candidatesUseCase = candidatesUseCase;
        this.issueBatchUseCase = issueBatchUseCase;
    }

    @GetMapping("/exam-certificates/candidates")
    @PreAuthorize("hasAnyRole('DOCENTE','ADMINISTRATIVO','DIRECTOR')")
    public List<ExamCertificateCandidateResponse> candidates(@PathVariable Long id) {
        return candidatesUseCase.candidates(id);
    }

    @PostMapping("/exam-certificates/batch")
    @PreAuthorize("hasAnyRole('DOCENTE','ADMINISTRATIVO','DIRECTOR')")
    public ExamCertificateBatchResponse issueBatch(@PathVariable Long id, @Valid @RequestBody IssueExamCertificateBatchRequest request) {
        return issueBatchUseCase.issueBatch(id, request);
    }
}
