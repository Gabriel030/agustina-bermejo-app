package com.red_escuela.backend.billing.payment.controller;

import com.red_escuela.backend.billing.payment.dto.CashPaymentPreviewRequest;
import com.red_escuela.backend.billing.payment.dto.CashPaymentPreviewResponse;
import com.red_escuela.backend.billing.payment.dto.CashPaymentResponse;
import com.red_escuela.backend.billing.payment.dto.RegisterCashPaymentRequest;
import com.red_escuela.backend.billing.payment.dto.StudentPaymentResponse;
import com.red_escuela.backend.billing.payment.usecase.ListStudentPaymentsUseCase;
import com.red_escuela.backend.billing.payment.usecase.PreviewCashPaymentUseCase;
import com.red_escuela.backend.billing.payment.usecase.RegisterCashPaymentUseCase;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class StudentPaymentController {
    private final RegisterCashPaymentUseCase registerCashPaymentUseCase;
    private final PreviewCashPaymentUseCase previewCashPaymentUseCase;
    private final ListStudentPaymentsUseCase listStudentPaymentsUseCase;
    private final CurrentStudentService currentStudentService;

    public StudentPaymentController(RegisterCashPaymentUseCase registerCashPaymentUseCase, PreviewCashPaymentUseCase previewCashPaymentUseCase,
                                    ListStudentPaymentsUseCase listStudentPaymentsUseCase, CurrentStudentService currentStudentService) {
        this.registerCashPaymentUseCase = registerCashPaymentUseCase;
        this.previewCashPaymentUseCase = previewCashPaymentUseCase;
        this.listStudentPaymentsUseCase = listStudentPaymentsUseCase;
        this.currentStudentService = currentStudentService;
    }

    @PostMapping("/students/{studentId}/cash-payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "IDEMPOTENCY_KEY_INVALID, CASH_PAYMENT_AMOUNT_INVALID, CASH_PAYMENT_RECEIPT_NUMBER_INVALID, PAYMENT_DATE_IN_FUTURE, CASH_PAYMENT_DATE_TOO_OLD (más de 5 días atrás para el administrativo), CASH_PAYMENT_BACKDATE_REASON_REQUIRED (director, más de 5 días atrás sin motivo), CASH_PAYMENT_BACKDATE_REASON_INVALID (motivo de más de 500 caracteres)"),
            @ApiResponse(responseCode = "404", description = "STUDENT_NOT_FOUND: el alumno no existe"),
            @ApiResponse(responseCode = "409", description = "IDEMPOTENCY_KEY_CONFLICT (misma clave con otro pedido) o CASH_PAYMENT_PREVIEW_OUTDATED (la cuenta cambió desde la vista previa: pedirla de nuevo)")})
    public CashPaymentResponse registerCashPayment(@PathVariable Long studentId, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                   @Valid @RequestBody RegisterCashPaymentRequest request) {
        return CashPaymentResponse.from(registerCashPaymentUseCase.registerCashPayment(studentId, request, idempotencyKey));
    }

    @PostMapping("/students/{studentId}/cash-payments/preview")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "CASH_PAYMENT_AMOUNT_INVALID"),
            @ApiResponse(responseCode = "404", description = "El alumno no existe")})
    public CashPaymentPreviewResponse previewCashPayment(@PathVariable Long studentId, @Valid @RequestBody CashPaymentPreviewRequest request) {
        return CashPaymentPreviewResponse.from(previewCashPaymentUseCase.previewCashPayment(studentId, request.amount()));
    }

    @GetMapping("/students/{studentId}/payments")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<StudentPaymentResponse> listPaymentsOfStudent(@PathVariable Long studentId) {
        return listStudentPaymentsUseCase.listPaymentsOfStudent(studentId).stream().map(StudentPaymentResponse::from).toList();
    }

    @GetMapping("/me/payments")
    @PreAuthorize("hasRole('ALUMNO')")
    public List<StudentPaymentResponse> listOwnPayments() {
        return listStudentPaymentsUseCase.listPaymentsOfCurrentStudent(currentStudentService.resolveCurrentStudent().getId()).stream()
                .map(StudentPaymentResponse::from).toList();
    }
}
