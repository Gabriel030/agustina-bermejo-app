package com.red_escuela.backend.graduation.certificate.exam.controller;

import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateBatchResponse;
import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateCandidateResponse;
import com.red_escuela.backend.graduation.certificate.exam.dto.IssueExamCertificateBatchRequest;
import com.red_escuela.backend.graduation.certificate.exam.usecase.IssueExamCertificateBatchUseCase;
import com.red_escuela.backend.graduation.certificate.exam.usecase.ListExamCertificateCandidatesUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
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

    @Operation(description = "Elegibles: alumnos presentes con la mesa rendida (ExamBoardStatus CLOSED, acta cerrada; si no, EXAM_CERTIFICATE_BOARD_NOT_HELD). NOT_ELIGIBLE_DEBT no es seleccionable y no informa montos, excepción ni link. REVOKED es seleccionable y el lote lo reemite. issuedAt informa ALREADY_ISSUED y REVOKED. Docente de la mesa (404 si es ajena), Administrativo y Director; el Preceptor no tiene acceso.")
    @GetMapping("/exam-certificates/candidates")
    @RequiresPermission(code = "certificate.exam_candidates_view", legacyRoles = "ROLE_DOCENTE,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public List<ExamCertificateCandidateResponse> candidates(@PathVariable Long id) {
        return candidatesUseCase.candidates(id);
    }

    @Operation(description = "Emisión en lote: omite con outcome DEBT a quien no esté al día con los pagos o pase a deber entre candidatos y emisión (sin montos); reemite las REVOKED; skippedCount cuenta los omitidos. Misma condición de mesa rendida y mismos roles que candidates.")
    @PostMapping("/exam-certificates/batch")
    @RequiresPermission(code = "certificate.exam_issue_batch", legacyRoles = "ROLE_DOCENTE,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public ExamCertificateBatchResponse issueBatch(@PathVariable Long id, @Valid @RequestBody IssueExamCertificateBatchRequest request) {
        return issueBatchUseCase.issueBatch(id, request);
    }
}
