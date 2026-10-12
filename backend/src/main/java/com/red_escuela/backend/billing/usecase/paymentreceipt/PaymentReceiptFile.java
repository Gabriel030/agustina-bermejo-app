package com.red_escuela.backend.billing.usecase.paymentreceipt;

public record PaymentReceiptFile(String fileName, String contentType, byte[] content) {
}
