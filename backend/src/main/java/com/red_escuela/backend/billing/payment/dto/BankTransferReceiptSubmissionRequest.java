package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.shared.domain.Currency;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BankTransferReceiptSubmissionRequest(MultipartFile file, List<Long> invoiceIds, BigDecimal declaredAmount, Currency currency, LocalDate transferDate,
                                                   String operationNumber, String originAccount, String holderName, String bankName) {

    public BankTransferReceiptSubmissionRequest {
        invoiceIds = invoiceIds == null ? List.of() : List.copyOf(invoiceIds);
    }

    public boolean hasFile() {
        return file != null && !file.isEmpty();
    }
}
