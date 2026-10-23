package com.red_escuela.backend.billing.payment.controller;

import com.red_escuela.backend.billing.payment.domain.RefundRequestResult;
import com.red_escuela.backend.billing.payment.dto.CompleteRefundRequest;
import com.red_escuela.backend.billing.payment.dto.FailRefundRequest;
import com.red_escuela.backend.billing.payment.dto.RefundRequestResponse;
import com.red_escuela.backend.billing.payment.dto.RefundResponse;
import com.red_escuela.backend.billing.payment.dto.RequestRefundRequest;
import com.red_escuela.backend.billing.payment.usecase.CompleteRefundUseCase;
import com.red_escuela.backend.billing.payment.usecase.FailRefundUseCase;
import com.red_escuela.backend.billing.payment.usecase.RequestRefundUseCase;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RefundController {
    private final RequestRefundUseCase requestRefundUseCase;
    private final CompleteRefundUseCase completeRefundUseCase;
    private final FailRefundUseCase failRefundUseCase;
    private final CurrentStudentService currentStudentService;

    public RefundController(RequestRefundUseCase requestRefundUseCase, CompleteRefundUseCase completeRefundUseCase, FailRefundUseCase failRefundUseCase,
                            CurrentStudentService currentStudentService) {
        this.requestRefundUseCase = requestRefundUseCase;
        this.completeRefundUseCase = completeRefundUseCase;
        this.failRefundUseCase = failRefundUseCase;
        this.currentStudentService = currentStudentService;
    }

    @PostMapping("/me/refund-requests")
    @PreAuthorize("hasRole('ALUMNO')")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Pedido abierto en Aprobaciones; devuelve authorizationRequestId y las partes del reparto"),
            @ApiResponse(responseCode = "400", description = "REFUND_AMOUNT_INVALID, REFUND_DESTINATION_INVALID, REFUND_DESTINATION_REQUIRED"),
            @ApiResponse(responseCode = "409", description = "INSUFFICIENT_ACCOUNT_CREDIT, REFUND_BLOCKED_BY_OVERDUE_DEBT (D-264) o REFUND_EXCEEDS_AVAILABLE_AMOUNT")})
    public ResponseEntity<RefundRequestResponse> requestOwnRefund(@Valid @RequestBody RequestRefundRequest request) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return respondTo(requestRefundUseCase.requestOwnRefund(studentId, request));
    }

    @PostMapping("/students/{studentId}/refund-requests")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pedido cargado por el director: partes aprobadas directo (D-216)"),
            @ApiResponse(responseCode = "202", description = "Pedido del administrativo abierto en Aprobaciones"),
            @ApiResponse(responseCode = "400", description = "REFUND_AMOUNT_INVALID, REFUND_DESTINATION_INVALID, REFUND_DESTINATION_REQUIRED"),
            @ApiResponse(responseCode = "409", description = "INSUFFICIENT_ACCOUNT_CREDIT, REFUND_BLOCKED_BY_OVERDUE_DEBT (D-264) o REFUND_EXCEEDS_AVAILABLE_AMOUNT")})
    public ResponseEntity<RefundRequestResponse> requestRefundOnBehalfOfStudent(@PathVariable Long studentId, @Valid @RequestBody RequestRefundRequest request) {
        return respondTo(requestRefundUseCase.requestRefundOnBehalfOfStudent(studentId, request));
    }

    @PostMapping("/refunds/{refundId}/completion")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "REFUND_COMPLETION_INVALID"),
            @ApiResponse(responseCode = "404", description = "REFUND_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "ILLEGAL_REFUND_STATUS_TRANSITION (la devolución no está aprobada), REFUND_METHOD_MISMATCH o INSUFFICIENT_ACCOUNT_CREDIT")})
    public RefundResponse completeRefund(@PathVariable Long refundId, @Valid @RequestBody CompleteRefundRequest request) {
        return RefundResponse.from(completeRefundUseCase.completeRefund(refundId, request));
    }

    @PostMapping("/refunds/{refundId}/failure")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "REFUND_FAILURE_REASON_INVALID"),
            @ApiResponse(responseCode = "404", description = "REFUND_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "ILLEGAL_REFUND_STATUS_TRANSITION (la devolución no está aprobada)")})
    public RefundResponse failRefund(@PathVariable Long refundId, @Valid @RequestBody FailRefundRequest request) {
        return RefundResponse.from(failRefundUseCase.failRefund(refundId, request));
    }

    private ResponseEntity<RefundRequestResponse> respondTo(RefundRequestResult result) {
        HttpStatus status = result.isAwaitingAuthorization() ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(RefundRequestResponse.from(result));
    }
}
