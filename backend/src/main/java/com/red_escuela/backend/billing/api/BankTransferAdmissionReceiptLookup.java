package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
public class BankTransferAdmissionReceiptLookup implements AdmissionReceiptLookup {

    private final BankTransferReceiptRepository receiptRepository;

    public BankTransferAdmissionReceiptLookup(BankTransferReceiptRepository receiptRepository) {
        this.receiptRepository = receiptRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, AdmissionReceiptSummary> findLatestReceiptByInvoiceIds(Collection<Long> invoiceIds) {
        Map<Long, AdmissionReceiptSummary> latestByInvoiceId = new HashMap<>();
        if (invoiceIds.isEmpty()) {
            return latestByInvoiceId;
        }
        receiptRepository.findByInvoiceIdsWithPeople(invoiceIds).forEach(receipt -> latestByInvoiceId.putIfAbsent(
                receipt.getInvoice().getId(), new AdmissionReceiptSummary(receipt.getStatus().name(), receipt.getUploadedAt())));
        return latestByInvoiceId;
    }
}
