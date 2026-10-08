package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.invoice.service.InvoiceViewLoader;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InvoiceDetailLoader {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceViewLoader invoiceViewLoader;
    private final BankTransferReceiptRepository bankTransferReceiptRepository;

    public InvoiceDetailLoader(InvoiceRepository invoiceRepository, InvoiceViewLoader invoiceViewLoader,
                               BankTransferReceiptRepository bankTransferReceiptRepository) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceViewLoader = invoiceViewLoader;
        this.bankTransferReceiptRepository = bankTransferReceiptRepository;
    }

    @Transactional(readOnly = true)
    public List<InvoiceWithReceipts> byCourseEnrollment(Long courseEnrollmentId) {
        return attachReceipts(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(courseEnrollmentId));
    }

    @Transactional(readOnly = true)
    public List<InvoiceWithReceipts> byProgramEnrollment(Long programEnrollmentId) {
        return attachReceipts(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(programEnrollmentId));
    }

    private List<InvoiceWithReceipts> attachReceipts(List<Invoice> invoices) {
        if (invoices.isEmpty()) {
            return List.of();
        }
        List<Long> ids = invoices.stream().map(Invoice::getId).toList();
        Map<Long, List<BankTransferReceipt>> receiptsByInvoiceId = bankTransferReceiptRepository.findByInvoiceIdsWithPeople(ids).stream()
                .collect(Collectors.groupingBy(receipt -> receipt.getInvoice().getId()));
        return invoiceViewLoader.loadInvoiceViews(invoices).stream()
                .map(view -> new InvoiceWithReceipts(view, receiptsByInvoiceId.getOrDefault(view.invoice().getId(), List.of())))
                .toList();
    }
}
