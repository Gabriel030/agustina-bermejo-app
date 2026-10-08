package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;

import java.util.List;

public record InvoiceWithReceipts(InvoiceView view, List<BankTransferReceipt> receipts) {
}
