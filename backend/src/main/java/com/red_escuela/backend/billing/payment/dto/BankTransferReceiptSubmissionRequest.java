package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.billing.payment.exception.BankTransferDetailsRequiredException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.modulith.NamedInterface;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@NamedInterface("api")
public record BankTransferReceiptSubmissionRequest(MultipartFile file, List<Long> invoiceIds, Money declaredAmount, LocalDate transferDate,
                                                   String operationNumber, String originAccount, String holderName, String bankName) {

    public BankTransferReceiptSubmissionRequest {
        invoiceIds = invoiceIds == null ? List.of() : List.copyOf(invoiceIds);
    }

    public static BankTransferReceiptSubmissionRequest fromForm(MultipartFile file, List<Long> invoiceIds, BigDecimal declaredAmount, Currency currency,
                                                                LocalDate transferDate, String operationNumber, String originAccount, String holderName,
                                                                String bankName) {
        return new BankTransferReceiptSubmissionRequest(file, invoiceIds, moneyOf(declaredAmount, currency), transferDate, operationNumber, originAccount, holderName,
                bankName);
    }

    public BankTransferReceiptSubmissionRequest withAdditionalInvoice(Long invoiceId) {
        List<Long> all = new ArrayList<>(invoiceIds);
        all.add(invoiceId);
        return new BankTransferReceiptSubmissionRequest(file, all, declaredAmount, transferDate, operationNumber, originAccount, holderName, bankName);
    }

    private static Money moneyOf(BigDecimal declaredAmount, Currency currency) {
        if (declaredAmount == null) {
            return null;
        }
        if (currency == null) {
            throw BankTransferDetailsRequiredException.currencyRequired();
        }
        return Money.of(declaredAmount, currency);
    }

    public boolean hasFile() {
        return file != null && !file.isEmpty();
    }
}
