package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
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
        Map<Long, BankTransferReceipt> latestReceiptByInvoiceId = new HashMap<>();
        receiptRepository.findByInvoiceIdsWithPeople(invoiceIds).forEach(receipt -> latestReceiptByInvoiceId.putIfAbsent(receipt.getInvoice().getId(), receipt));
        receiptRepository.findByCheckoutItemInvoiceIds(invoiceIds).forEach(found -> latestReceiptByInvoiceId.merge(found.getInvoiceId(), found.getReceipt(),
                BankTransferAdmissionReceiptLookup::moreRecent));
        latestReceiptByInvoiceId.forEach((invoiceId, receipt) -> latestByInvoiceId.put(invoiceId, new AdmissionReceiptSummary(receipt.getStatus().name(), receipt.getUploadedAt())));
        return latestByInvoiceId;
    }

    private static BankTransferReceipt moreRecent(BankTransferReceipt first, BankTransferReceipt second) {
        int byUploadTime = first.getUploadedAt().compareTo(second.getUploadedAt());
        if (byUploadTime != 0) {
            return byUploadTime > 0 ? first : second;
        }
        return first.getId() >= second.getId() ? first : second;
    }
}
