package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InvoiceDetailLoader {
    private final InvoiceRepository invoiceRepository;
    private final BankTransferReceiptRepository bankTransferReceiptRepository;

    public InvoiceDetailLoader(InvoiceRepository invoiceRepository, BankTransferReceiptRepository bankTransferReceiptRepository) {
        this.invoiceRepository = invoiceRepository;
        this.bankTransferReceiptRepository = bankTransferReceiptRepository;
    }

    @Transactional(readOnly = true)
    public List<InvoiceWithReceipts> byCourseEnrollment(Long courseEnrollmentId) {
        return attachReceipts(invoiceRepository.findByCourseEnrollmentIdWithPayments(courseEnrollmentId));
    }

    @Transactional(readOnly = true)
    public List<InvoiceWithReceipts> byProgramEnrollment(Long programEnrollmentId) {
        return attachReceipts(invoiceRepository.findByProgramEnrollmentIdWithPayments(programEnrollmentId));
    }

    private List<InvoiceWithReceipts> attachReceipts(List<Invoice> invoices) {
        if (invoices.isEmpty()) {
            return List.of();
        }
        List<Long> ids = invoices.stream().map(Invoice::getId).toList();
        invoiceRepository.fetchAdjustments(ids);
        Map<Long, List<BankTransferReceipt>> receiptsByInvoiceId = bankTransferReceiptRepository.findByInvoiceIdsWithPeople(ids).stream()
                .collect(Collectors.groupingBy(receipt -> receipt.getInvoice().getId()));
        return invoices.stream()
                .map(invoice -> new InvoiceWithReceipts(invoice, receiptsByInvoiceId.getOrDefault(invoice.getId(), List.of())))
                .toList();
    }
}
