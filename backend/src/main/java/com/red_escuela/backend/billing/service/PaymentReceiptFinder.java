package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.exception.PaymentReceiptNotFoundException;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.stereotype.Service;

@Service
public class PaymentReceiptFinder {

    private final BankTransferReceiptRepository bankTransferReceiptRepository;
    private final InvoiceRepository invoiceRepository;

    public PaymentReceiptFinder(BankTransferReceiptRepository bankTransferReceiptRepository, InvoiceRepository invoiceRepository) {
        this.bankTransferReceiptRepository = bankTransferReceiptRepository;
        this.invoiceRepository = invoiceRepository;
    }

    public BankTransferReceipt findOrThrow(Long id) {
        return bankTransferReceiptRepository.findById(id).orElseThrow(() -> new PaymentReceiptNotFoundException(id));
    }

    public Long reviewerIdOf(BankTransferReceipt receipt) {
        return receipt.getReviewedBy().getId();
    }

    public Person payerOf(BankTransferReceipt receipt) {
        return invoiceRepository.findOwnerPersonByInvoiceId(receipt.getInvoice().getId()).orElse(null);
    }

    public void linkApprovedPayment(BankTransferReceipt receipt, Long paymentId) {
        receipt.setPaymentId(paymentId);
    }
}
