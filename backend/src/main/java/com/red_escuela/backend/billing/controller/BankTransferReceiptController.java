package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.BankTransferReceiptResponse;
import com.red_escuela.backend.billing.invoice.service.BankTransferReceiptResponseAssembler;
import com.red_escuela.backend.billing.payment.dto.ApproveBankTransferReceiptRequest;
import com.red_escuela.backend.billing.dto.RejectPaymentReceiptRequest;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.GetPaymentReceiptFileUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.PaymentReceiptFile;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ListPendingPaymentReceiptsUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.RejectPaymentReceiptUseCase;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.module.RequiresModule;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiresModule(RequiresModule.BILLING)
@RequestMapping("/bank-transfer-receipts")
public class BankTransferReceiptController {

    private final ListPendingPaymentReceiptsUseCase listPendingPaymentReceiptsUseCase;
    private final ApprovePaymentReceiptUseCase approvePaymentReceiptUseCase;
    private final RejectPaymentReceiptUseCase rejectPaymentReceiptUseCase;
    private final GetPaymentReceiptFileUseCase getPaymentReceiptFileUseCase;
    private final CurrentPersonService currentPersonService;
    private final BankTransferReceiptResponseAssembler receiptResponseAssembler;

    public BankTransferReceiptController(ListPendingPaymentReceiptsUseCase listPendingPaymentReceiptsUseCase,
                                     ApprovePaymentReceiptUseCase approvePaymentReceiptUseCase, RejectPaymentReceiptUseCase rejectPaymentReceiptUseCase,
                                     GetPaymentReceiptFileUseCase getPaymentReceiptFileUseCase,
                                     CurrentPersonService currentPersonService,
                                     BankTransferReceiptResponseAssembler receiptResponseAssembler) {
        this.listPendingPaymentReceiptsUseCase = listPendingPaymentReceiptsUseCase;
        this.approvePaymentReceiptUseCase = approvePaymentReceiptUseCase;
        this.rejectPaymentReceiptUseCase = rejectPaymentReceiptUseCase;
        this.getPaymentReceiptFileUseCase = getPaymentReceiptFileUseCase;
        this.currentPersonService = currentPersonService;
        this.receiptResponseAssembler = receiptResponseAssembler;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<BankTransferReceiptResponse> listPending() {
        return receiptResponseAssembler.assembleReviewerReceiptResponses(listPendingPaymentReceiptsUseCase.list());
    }

    @GetMapping("/{id}/file")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        PaymentReceiptFile file = getPaymentReceiptFileUseCase.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.fileName()).build().toString())
                .body(file.content());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public BankTransferReceiptResponse approve(@PathVariable Long id, @RequestBody(required = false) ApproveBankTransferReceiptRequest request) {
        PaymentVerification verification = request == null ? PaymentVerification.none() : request.toVerification();
        return receiptResponseAssembler.assembleReviewerReceiptResponse(approvePaymentReceiptUseCase.approve(id, currentPersonService.resolveCurrentPerson(), verification));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public BankTransferReceiptResponse reject(@PathVariable Long id, @RequestBody(required = false) RejectPaymentReceiptRequest request) {
        String rejectionReason = request == null ? null : request.rejectionReason();
        return receiptResponseAssembler.assembleReviewerReceiptResponse(
                rejectPaymentReceiptUseCase.reject(id, rejectionReason, currentPersonService.resolveCurrentPerson()));
    }
}
