package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.dto.BankTransferReceiptResponse;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.operation.ReceiptInvoiceIdsLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class BankTransferReceiptResponseAssembler {

    private final InvoiceViewLoader invoiceViewLoader;
    private final InvoiceRepository invoiceRepository;
    private final ReceiptInvoiceIdsLookup receiptInvoiceIds;

    public BankTransferReceiptResponseAssembler(InvoiceViewLoader invoiceViewLoader, InvoiceRepository invoiceRepository, ReceiptInvoiceIdsLookup receiptInvoiceIds) {
        this.invoiceViewLoader = invoiceViewLoader;
        this.invoiceRepository = invoiceRepository;
        this.receiptInvoiceIds = receiptInvoiceIds;
    }

    @Transactional(readOnly = true)
    public BankTransferReceiptResponse assembleBankTransferReceiptResponse(BankTransferReceipt receipt) {
        return assemble(receipt, invoiceIdsByPaymentId(List.of(receipt)));
    }

    @Transactional(readOnly = true)
    public List<BankTransferReceiptResponse> assembleBankTransferReceiptResponses(List<BankTransferReceipt> receipts) {
        Map<Long, List<Long>> invoiceIdsByPaymentId = invoiceIdsByPaymentId(receipts);
        return receipts.stream().map(receipt -> assemble(receipt, invoiceIdsByPaymentId)).toList();
    }

    private Map<Long, List<Long>> invoiceIdsByPaymentId(List<BankTransferReceipt> receipts) {
        return receiptInvoiceIds.findInvoiceIdsByPaymentId(receipts.stream().map(BankTransferReceipt::getPaymentId).filter(Objects::nonNull).distinct().toList());
    }

    private BankTransferReceiptResponse assemble(BankTransferReceipt receipt, Map<Long, List<Long>> invoiceIdsByPaymentId) {
        Invoice invoice = receipt.getInvoice();
        String productName = invoice.findCourseEnrollmentId().flatMap(invoiceViewLoader::findCourseEnrollment).map(e -> e.getCourse().getName())
                .or(() -> invoice.findProgramEnrollmentId().flatMap(invoiceViewLoader::findProgramEnrollment).map(e -> e.getProgram().getName()))
                .orElse(null);
        List<Long> invoiceIds = receipt.getPaymentId() == null ? List.of(invoice.getId()) : invoiceIdsByPaymentId.getOrDefault(receipt.getPaymentId(), List.of(invoice.getId()));
        return BankTransferReceiptResponse.from(receipt, invoiceRepository.findOwnerPersonByInvoiceId(invoice.getId()).orElse(null), productName, invoiceIds);
    }
}
