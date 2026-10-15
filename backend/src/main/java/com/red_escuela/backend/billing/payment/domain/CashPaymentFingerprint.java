package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

public final class CashPaymentFingerprint {

    private CashPaymentFingerprint() {
    }

    public static String of(Long studentId, Currency currency, List<CashPaymentPreview.CoveredInvoice> coveredInvoices, NonNegativeMoney unallocatedAfterwards) {
        String covered = coveredInvoices.stream()
                .map(invoice -> invoice.invoiceId() + "=" + invoice.amount().value().amount().toPlainString())
                .collect(Collectors.joining(","));
        String canonical = studentId + "|" + currency + "|" + covered + "|" + unallocatedAfterwards.value().amount().toPlainString();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The cash payment fingerprint could not be computed", exception);
        }
    }
}
