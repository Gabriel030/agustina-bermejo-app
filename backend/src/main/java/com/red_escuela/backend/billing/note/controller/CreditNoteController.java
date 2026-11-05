package com.red_escuela.backend.billing.note.controller;

import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.dto.CreateCreditNoteRequest;
import com.red_escuela.backend.billing.note.dto.CreditNotePendingAuthorizationResponse;
import com.red_escuela.backend.billing.note.dto.CreditNoteResponse;
import com.red_escuela.backend.billing.note.usecase.IssueCreditNoteUseCase;
import com.red_escuela.backend.billing.note.usecase.ListCreditNotesOfStudentUseCase;
import com.red_escuela.backend.billing.note.usecase.ListCreditNotesOfStudentWithAuthorshipUseCase;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiresModule(RequiresModule.BILLING)
public class CreditNoteController {
    private final IssueCreditNoteUseCase issueCreditNoteUseCase;
    private final ListCreditNotesOfStudentUseCase listCreditNotesOfStudentUseCase;
    private final CurrentStudentService currentStudentService;

    private final ListCreditNotesOfStudentWithAuthorshipUseCase listCreditNotesWithAuthorshipUseCase;

    public CreditNoteController(IssueCreditNoteUseCase issueCreditNoteUseCase, ListCreditNotesOfStudentUseCase listCreditNotesOfStudentUseCase,
                                CurrentStudentService currentStudentService, ListCreditNotesOfStudentWithAuthorshipUseCase listCreditNotesWithAuthorshipUseCase) {
        this.listCreditNotesWithAuthorshipUseCase = listCreditNotesWithAuthorshipUseCase;
        this.issueCreditNoteUseCase = issueCreditNoteUseCase;
        this.listCreditNotesOfStudentUseCase = listCreditNotesOfStudentUseCase;
        this.currentStudentService = currentStudentService;
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @PostMapping("/invoices/{invoiceId}/credit-notes")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "IDEMPOTENCY_KEY_INVALID: la clave está vacía o tiene más de 100 caracteres"),
            @ApiResponse(responseCode = "409", description = "IDEMPOTENCY_KEY_CONFLICT: la misma clave se usó con otro pedido")})
    public ResponseEntity<?> issueOrRequestCreditNote(@PathVariable Long invoiceId,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                       @Valid @RequestBody CreateCreditNoteRequest request) {
        BillingOperationOutcome<CreditNote> outcome = issueCreditNoteUseCase.issueOrRequestCreditNote(invoiceId, request, idempotencyKey);
        return switch (outcome) {
            case BillingOperationOutcome.Processed<CreditNote> processed -> ResponseEntity.status(HttpStatus.CREATED).body(CreditNoteResponse.from(processed.result()));
            case BillingOperationOutcome.AwaitingAuthorization<CreditNote> awaiting ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(new CreditNotePendingAuthorizationResponse(awaiting.authorizationRequestId()));
        };
    }

    @GetMapping("/students/{studentId}/credit-notes")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<CreditNoteResponse> listCreditNotesOfStudent(@PathVariable Long studentId) {
        return listCreditNotesWithAuthorshipUseCase.listCreditNotesOfStudentWithAuthorship(studentId);
    }

    @GetMapping("/me/credit-notes")
    @PreAuthorize("hasRole('ALUMNO')")
    public List<CreditNoteResponse> listMyCreditNotes() {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return listCreditNotesOfStudentUseCase.listCreditNotesOfStudent(studentId);
    }
}
