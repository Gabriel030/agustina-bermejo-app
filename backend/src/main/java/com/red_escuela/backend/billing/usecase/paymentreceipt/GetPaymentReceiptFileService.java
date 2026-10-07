package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.exception.PaymentReceiptFileNotFoundException;
import com.red_escuela.backend.billing.service.PaymentReceiptFinder;
import com.red_escuela.backend.shared.storage.FileStorageService;
import com.red_escuela.backend.shared.UseCase;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

@UseCase
public class GetPaymentReceiptFileService implements GetPaymentReceiptFileUseCase {
    private static final Map<String, String> CONTENT_TYPES_BY_EXTENSION = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png"
    );
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final PaymentReceiptFinder paymentReceiptFinder;
    private final FileStorageService fileStorageService;

    public GetPaymentReceiptFileService(PaymentReceiptFinder paymentReceiptFinder, FileStorageService fileStorageService) {
        this.paymentReceiptFinder = paymentReceiptFinder;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public PaymentReceiptFile get(Long receiptId) {
        BankTransferReceipt receipt = paymentReceiptFinder.findOrThrow(receiptId);
        String fileName = Path.of(receipt.getFilePath()).getFileName().toString();
        try {
            return new PaymentReceiptFile(fileName, contentTypeOf(fileName), fileStorageService.readBytes(receipt.getFilePath()));
        } catch (UncheckedIOException e) {
            throw new PaymentReceiptFileNotFoundException(receiptId, e);
        }
    }

    private String contentTypeOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return DEFAULT_CONTENT_TYPE;
        }
        return CONTENT_TYPES_BY_EXTENSION.getOrDefault(fileName.substring(dot + 1).toLowerCase(Locale.ROOT), DEFAULT_CONTENT_TYPE);
    }
}
