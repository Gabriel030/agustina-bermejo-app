package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PaymentReceipt;

import java.util.List;

public record InvoiceWithReceipts(Invoice invoice, List<PaymentReceipt> receipts) {
}
