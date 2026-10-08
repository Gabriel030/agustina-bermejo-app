package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AdmissionInvoiceReceiptFinder {

    private final BankTransferReceiptRepository receiptRepository;

    public AdmissionInvoiceReceiptFinder(BankTransferReceiptRepository receiptRepository) {
        this.receiptRepository = receiptRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, BankTransferReceipt> findLatestReceiptByAdmissionInvoiceId(List<ProgramEnrollmentApplication> applications) {
        List<Long> invoiceIds = applications.stream()
                .map(ProgramEnrollmentApplication::getAdmissionInvoice)
                .filter(Objects::nonNull)
                .map(invoice -> invoice.getId())
                .toList();
        Map<Long, BankTransferReceipt> latestByInvoiceId = new HashMap<>();
        if (invoiceIds.isEmpty()) {
            return latestByInvoiceId;
        }
        receiptRepository.findByInvoiceIdsWithPeople(invoiceIds)
                .forEach(receipt -> latestByInvoiceId.putIfAbsent(receipt.getInvoice().getId(), receipt));
        return latestByInvoiceId;
    }
}
