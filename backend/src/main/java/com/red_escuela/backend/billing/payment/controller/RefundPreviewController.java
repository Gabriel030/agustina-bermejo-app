package com.red_escuela.backend.billing.payment.controller;

import com.red_escuela.backend.billing.payment.dto.RefundEligibilityResponse;
import com.red_escuela.backend.billing.payment.dto.RefundPreviewResponse;
import com.red_escuela.backend.billing.payment.dto.RequestRefundRequest;
import com.red_escuela.backend.billing.payment.usecase.PreviewRefundUseCase;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import com.red_escuela.backend.shared.module.RequiresModule;
import com.red_escuela.backend.shared.domain.Currency;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiresModule(RequiresModule.BILLING)
public class RefundPreviewController {
    private static final String CURRENCY_DEFAULT = "ARS";

    private final PreviewRefundUseCase previewRefundUseCase;
    private final CurrentStudentService currentStudentService;

    public RefundPreviewController(PreviewRefundUseCase previewRefundUseCase, CurrentStudentService currentStudentService) {
        this.previewRefundUseCase = previewRefundUseCase;
        this.currentStudentService = currentStudentService;
    }

    @PostMapping("/me/refund-requests/preview")
    @PreAuthorize("hasRole('ALUMNO')")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Reparto que el sistema aplicaría; si hay bloqueo, allowed=false y blockedBy con el código de la regla")})
    public RefundPreviewResponse previewOwnRefund(@Valid @RequestBody RequestRefundRequest request) {
        return RefundPreviewResponse.from(previewRefundUseCase.previewOwnRefund(currentStudentService.resolveCurrentStudent().getId(), request));
    }

    @PostMapping("/students/{studentId}/refund-requests/preview")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reparto que el sistema aplicaría; si hay bloqueo, allowed=false y blockedBy con el código de la regla"),
            @ApiResponse(responseCode = "404", description = "STUDENT_NOT_FOUND: el alumno no existe")})
    public RefundPreviewResponse previewRefundOnBehalfOfStudent(@PathVariable Long studentId, @Valid @RequestBody RequestRefundRequest request) {
        return RefundPreviewResponse.from(previewRefundUseCase.previewRefundOnBehalfOfStudent(studentId, request));
    }

    @GetMapping("/me/refund-eligibility")
    @PreAuthorize("hasRole('ALUMNO')")
    public RefundEligibilityResponse describeOwnRefundEligibility(@RequestParam(name = "currency", defaultValue = CURRENCY_DEFAULT) Currency currency) {
        return RefundEligibilityResponse.from(previewRefundUseCase.describeOwnRefundEligibility(currentStudentService.resolveCurrentStudent().getId(), currency));
    }

    @GetMapping("/students/{studentId}/refund-eligibility")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({@ApiResponse(responseCode = "404", description = "STUDENT_NOT_FOUND: el alumno no existe")})
    public RefundEligibilityResponse describeRefundEligibilityOfStudent(@PathVariable Long studentId,
                                                                        @RequestParam(name = "currency", defaultValue = CURRENCY_DEFAULT) Currency currency) {
        return RefundEligibilityResponse.from(previewRefundUseCase.describeRefundEligibilityOfStudent(studentId, currency));
    }
}
