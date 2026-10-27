package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.authorization.domain.PendingInvoiceAuthorization;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.invoice.service.InvoiceView;

import java.util.List;

public record InvoiceWithReceipts(InvoiceView view, List<BankTransferReceipt> receipts, PendingInvoiceAuthorization pendingAuthorization) {

    public InvoiceWithReceipts(InvoiceView view, List<BankTransferReceipt> receipts) {
        this(view, receipts, null);
    }
}
