package com.red_escuela.backend.billing.payment.controller;

import com.red_escuela.backend.billing.payment.dto.BankTransferReceiptSubmissionRequest;
import com.red_escuela.backend.billing.payment.dto.BankTransferReceiptSubmissionResponse;
import com.red_escuela.backend.billing.payment.usecase.SubmitBankTransferReceiptUseCase;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import com.red_escuela.backend.shared.domain.Currency;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
public class BankTransferReceiptSubmissionController {
    private static final String SUBMISSION_ERRORS = "400: BANK_TRANSFER_DETAILS_REQUIRED con la violación del campo (BANK_TRANSFER_AMOUNT_INVALID, BANK_TRANSFER_DATE_REQUIRED, BANK_TRANSFER_DATE_IN_FUTURE, BANK_TRANSFER_OPERATION_NUMBER_REQUIRED, BANK_TRANSFER_ORIGIN_ACCOUNT_REQUIRED, BANK_TRANSFER_HOLDER_NAME_REQUIRED, BANK_TRANSFER_RECEIPT_FILE_REQUIRED, BANK_TRANSFER_INVOICES_REQUIRED, BANK_TRANSFER_CURRENCY_REQUIRED, BANK_TRANSFER_RECEIPT_FILE_TYPE_NOT_ALLOWED, BANK_TRANSFER_RECEIPT_FILE_CONTENT_MISMATCH), "
            + "RECEIPT_INVOICE_CURRENCY_MISMATCH, RECEIPT_INVOICE_NOT_PAYABLE, IDEMPOTENCY_KEY_INVALID";

    private final SubmitBankTransferReceiptUseCase submitBankTransferReceiptUseCase;
    private final CurrentStudentService currentStudentService;

    public BankTransferReceiptSubmissionController(SubmitBankTransferReceiptUseCase submitBankTransferReceiptUseCase, CurrentStudentService currentStudentService) {
        this.submitBankTransferReceiptUseCase = submitBankTransferReceiptUseCase;
        this.currentStudentService = currentStudentService;
    }

    @PostMapping(value = "/me/bank-transfer-receipts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ALUMNO')")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = SUBMISSION_ERRORS),
            @ApiResponse(responseCode = "404", description = "La factura no existe o no es del alumno"),
            @ApiResponse(responseCode = "409", description = "POSSIBLE_DUPLICATE_BANK_TRANSFER, RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST, INVOICE_ALREADY_UNDER_REVIEW, LEGACY_RECEIPT_PENDING_ON_INVOICE, IDEMPOTENCY_KEY_CONFLICT")})
    public BankTransferReceiptSubmissionResponse submitOwnReceipt(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                                  @RequestPart(name = "file", required = false) MultipartFile file,
                                                                  @RequestParam(name = "invoiceIds", required = false) List<Long> invoiceIds,
                                                                  @RequestParam(name = "declaredAmount", required = false) BigDecimal declaredAmount,
                                                                  @RequestParam(name = "currency", required = false) Currency currency,
                                                                  @RequestParam(name = "transferDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transferDate,
                                                                  @RequestParam(name = "operationNumber", required = false) String operationNumber,
                                                                  @RequestParam(name = "originAccount", required = false) String originAccount,
                                                                  @RequestParam(name = "holderName", required = false) String holderName,
                                                                  @RequestParam(name = "bankName", required = false) String bankName) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        BankTransferReceiptSubmissionRequest request = BankTransferReceiptSubmissionRequest.fromForm(file, invoiceIds, declaredAmount, currency, transferDate,
                operationNumber, originAccount, holderName, bankName);
        return BankTransferReceiptSubmissionResponse.from(submitBankTransferReceiptUseCase.submitReceiptOfOwnAccount(studentId, request, idempotencyKey));
    }

    @PostMapping(value = "/students/{studentId}/bank-transfer-receipts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = SUBMISSION_ERRORS),
            @ApiResponse(responseCode = "404", description = "El alumno no existe o la factura no es de su cuenta"),
            @ApiResponse(responseCode = "409", description = "POSSIBLE_DUPLICATE_BANK_TRANSFER, RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST, INVOICE_ALREADY_UNDER_REVIEW, LEGACY_RECEIPT_PENDING_ON_INVOICE, IDEMPOTENCY_KEY_CONFLICT")})
    public BankTransferReceiptSubmissionResponse submitReceiptOnBehalfOfStudent(@PathVariable Long studentId,
                                                                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                                               @RequestPart(name = "file", required = false) MultipartFile file,
                                                                               @RequestParam(name = "invoiceIds", required = false) List<Long> invoiceIds,
                                                                               @RequestParam(name = "declaredAmount", required = false) BigDecimal declaredAmount,
                                                                               @RequestParam(name = "currency", required = false) Currency currency,
                                                                               @RequestParam(name = "transferDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transferDate,
                                                                               @RequestParam(name = "operationNumber", required = false) String operationNumber,
                                                                               @RequestParam(name = "originAccount", required = false) String originAccount,
                                                                               @RequestParam(name = "holderName", required = false) String holderName,
                                                                               @RequestParam(name = "bankName", required = false) String bankName) {
        BankTransferReceiptSubmissionRequest request = BankTransferReceiptSubmissionRequest.fromForm(file, invoiceIds, declaredAmount, currency, transferDate,
                operationNumber, originAccount, holderName, bankName);
        return BankTransferReceiptSubmissionResponse.from(submitBankTransferReceiptUseCase.submitReceiptOnBehalfOfStudent(studentId, request, idempotencyKey));
    }
}
