package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.dto.BankTransferReceiptResponse;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BankTransferReceiptResponseAssembler {

    private final InvoiceViewLoader invoiceViewLoader;

    public BankTransferReceiptResponseAssembler(InvoiceViewLoader invoiceViewLoader) {
        this.invoiceViewLoader = invoiceViewLoader;
    }

    @Transactional(readOnly = true)
    public BankTransferReceiptResponse assembleBankTransferReceiptResponse(BankTransferReceipt receipt) {
        Invoice invoice = receipt.getInvoice();
        String productName = invoice.findCourseEnrollmentId().flatMap(invoiceViewLoader::findCourseEnrollment).map(e -> e.getCourse().getName())
                .or(() -> invoice.findProgramEnrollmentId().flatMap(invoiceViewLoader::findProgramEnrollment).map(e -> e.getProgram().getName()))
                .orElse(null);
        return BankTransferReceiptResponse.from(receipt, productName);
    }

    @Transactional(readOnly = true)
    public List<BankTransferReceiptResponse> assembleBankTransferReceiptResponses(List<BankTransferReceipt> receipts) {
        return receipts.stream().map(this::assembleBankTransferReceiptResponse).toList();
    }
}
