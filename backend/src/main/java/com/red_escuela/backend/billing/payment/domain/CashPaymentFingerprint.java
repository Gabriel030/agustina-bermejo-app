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

    public static String of(Long studentId, Currency currency, NonNegativeMoney amount, List<CashPaymentPreview.CoveredInvoice> coveredInvoices,
                            List<CashPaymentPreview.CoveredInvoice> coveredByRemainingCredit, NonNegativeMoney unallocatedAfterwards) {
        return digestOf(studentId + "|" + currency + "|" + amount.value().amount().toPlainString() + "|" + describe(coveredInvoices) + "|"
                + describe(coveredByRemainingCredit) + "|" + unallocatedAfterwards.value().amount().toPlainString());
    }

    private static String describe(List<CashPaymentPreview.CoveredInvoice> invoices) {
        return invoices.stream().map(invoice -> invoice.invoiceId() + "=" + invoice.amount().value().amount().toPlainString()).collect(Collectors.joining(","));
    }

    private static String digestOf(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The cash payment fingerprint could not be computed", exception);
        }
    }
}
