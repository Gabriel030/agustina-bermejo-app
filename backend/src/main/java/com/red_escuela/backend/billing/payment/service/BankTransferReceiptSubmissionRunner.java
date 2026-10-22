package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.domain.ReceiptFileSignature;
import com.red_escuela.backend.billing.payment.dto.BankTransferReceiptSubmissionRequest;
import com.red_escuela.backend.billing.payment.operation.ReceiptFileStorage;
import com.red_escuela.backend.billing.payment.operation.SubmitBankTransferReceiptCommand;
import com.red_escuela.backend.billing.payment.operation.SubmitBankTransferReceiptOperation;
import com.red_escuela.backend.billing.payment.policy.BankTransferReceiptFileCheck;
import com.red_escuela.backend.billing.payment.policy.BankTransferReceiptRejections;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.storage.FileStorageService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class BankTransferReceiptSubmissionRunner {
    private static final String RECEIPT_FOLDER = "comprobantes";

    private final BillingOperationPipeline pipeline;
    private final SubmitBankTransferReceiptOperation operation;
    private final PaymentActorResolver actorResolver;
    private final FileStorageService fileStorage;
    private final PolicySet<BankTransferReceiptFileCheck> filePolicies;

    public BankTransferReceiptSubmissionRunner(BillingOperationPipeline pipeline, SubmitBankTransferReceiptOperation operation, PaymentActorResolver actorResolver,
                                               FileStorageService fileStorage, PolicySet<BankTransferReceiptFileCheck> bankTransferReceiptFilePolicySet) {
        this.pipeline = pipeline;
        this.operation = operation;
        this.actorResolver = actorResolver;
        this.fileStorage = fileStorage;
        this.filePolicies = bankTransferReceiptFilePolicySet;
    }

    public BankTransferReceiptSubmissionResult runSubmission(Long studentId, BankTransferReceiptSubmissionRequest request, String idempotencyKey) {
        byte[] content = request.hasFile() ? readContent(request) : null;
        if (content != null) {
            filePolicies.evaluateRulesUntilFirstViolation(new BankTransferReceiptFileCheck(request.file().getContentType(), ReceiptFileSignature.detectContentType(content)))
                    .throwIfDenied(BankTransferReceiptRejections::rejectionOf);
        }
        ReceiptFileStorage storage = content == null ? null : ownerStudentId -> fileStorage.store(ownerStudentId, RECEIPT_FOLDER, request.file());
        SubmitBankTransferReceiptCommand command = new SubmitBankTransferReceiptCommand(studentId, request.invoiceIds(), request.declaredAmount(), request.transferDate(),
                request.operationNumber(), request.originAccount(), request.holderName(), request.bankName(), content == null ? null : sha256Of(content), storage,
                idempotencyKey, actorResolver.currentPersonAsActor());
        return pipeline.runOperation(operation, command).resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
    }

    private static byte[] readContent(BankTransferReceiptSubmissionRequest request) {
        try {
            return request.file().getBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String sha256Of(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is not available", failure);
        }
    }
}
