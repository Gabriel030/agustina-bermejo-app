package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.dto.BankTransferReceiptResponse;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.dto.ReceiptInvoiceSummary;
import com.red_escuela.backend.billing.invoice.dto.ReceiptReviewDetail;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.operation.ReceiptInvoiceIdsLookup;
import com.red_escuela.backend.billing.operation.ReceiptReviewFacts;
import com.red_escuela.backend.billing.operation.ReceiptReviewFactsLookup;
import com.red_escuela.backend.billing.operation.ReceiptSelectedInvoice;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class BankTransferReceiptResponseAssembler {

    private final InvoiceViewLoader invoiceViewLoader;
    private final InvoiceRepository invoiceRepository;
    private final ReceiptInvoiceIdsLookup receiptInvoiceIds;
    private final ReceiptReviewFactsLookup receiptReviewFacts;

    public BankTransferReceiptResponseAssembler(InvoiceViewLoader invoiceViewLoader, InvoiceRepository invoiceRepository, ReceiptInvoiceIdsLookup receiptInvoiceIds,
                                                ReceiptReviewFactsLookup receiptReviewFacts) {
        this.invoiceViewLoader = invoiceViewLoader;
        this.invoiceRepository = invoiceRepository;
        this.receiptInvoiceIds = receiptInvoiceIds;
        this.receiptReviewFacts = receiptReviewFacts;
    }

    @Transactional(readOnly = true)
    public BankTransferReceiptResponse assembleBankTransferReceiptResponse(BankTransferReceipt receipt) {
        return assemble(receipt, invoiceIdsByPaymentId(List.of(receipt)), null);
    }

    @Transactional(readOnly = true)
    public List<BankTransferReceiptResponse> assembleBankTransferReceiptResponses(List<BankTransferReceipt> receipts) {
        Map<Long, List<Long>> invoiceIdsByPaymentId = invoiceIdsByPaymentId(receipts);
        return receipts.stream().map(receipt -> assemble(receipt, invoiceIdsByPaymentId, null)).toList();
    }

    @Transactional(readOnly = true)
    public BankTransferReceiptResponse assembleReviewerReceiptResponse(BankTransferReceipt receipt) {
        return assembleReviewerReceiptResponses(List.of(receipt)).get(0);
    }

    @Transactional(readOnly = true)
    public List<BankTransferReceiptResponse> assembleReviewerReceiptResponses(List<BankTransferReceipt> receipts) {
        Map<Long, List<Long>> invoiceIdsByPaymentId = invoiceIdsByPaymentId(receipts);
        Map<Long, ReceiptReviewFacts> factsByReceiptId = receiptReviewFacts.findFactsByReceiptId(receipts.stream().map(BankTransferReceipt::getId).toList());
        Map<Long, List<ReceiptSelectedInvoice>> selectionsByPaymentId = receiptInvoiceIds.findSelectedInvoicesByPaymentId(paymentIdsOf(receipts));
        Map<Long, Invoice> invoicesById = invoicesById(receipts, selectionsByPaymentId);
        return receipts.stream().map(receipt -> assemble(receipt, invoiceIdsByPaymentId,
                reviewDetail(receipt, factsByReceiptId.get(receipt.getId()), selectionsByPaymentId, invoicesById))).toList();
    }

    private ReceiptReviewDetail reviewDetail(BankTransferReceipt receipt, ReceiptReviewFacts facts, Map<Long, List<ReceiptSelectedInvoice>> selectionsByPaymentId,
                                             Map<Long, Invoice> invoicesById) {
        List<ReceiptSelectedInvoice> legacySelection = List.of(new ReceiptSelectedInvoice(receipt.getInvoice().getId(), null));
        List<ReceiptSelectedInvoice> selections = receipt.getPaymentId() == null ? legacySelection
                : selectionsByPaymentId.getOrDefault(receipt.getPaymentId(), legacySelection);
        List<ReceiptInvoiceSummary> invoices = selections.stream().map(selection -> invoiceSummary(selection, invoicesById.get(selection.invoiceId()))).toList();
        if (facts == null) {
            return new ReceiptReviewDetail(null, null, null, null, null, invoices, null, null);
        }
        return new ReceiptReviewDetail(facts.declaredAmount(), facts.transferDate(), facts.operationNumber(), facts.originAccount(), facts.holderName(),
                invoices, facts.pendingBusinessDays(), facts.reviewAlertSentAt());
    }

    private ReceiptInvoiceSummary invoiceSummary(ReceiptSelectedInvoice selection, Invoice invoice) {
        Money outstandingAtSubmission = selection.outstandingAtSelection() == null ? null : selection.outstandingAtSelection().value();
        return new ReceiptInvoiceSummary(invoice.getId(), invoice.getPrimaryLine().getPeriod().toString(), invoice.getPrimaryLine().getFeeType().name(),
                invoice.getAmounts().total().value(), outstandingAtSubmission);
    }

    private Map<Long, Invoice> invoicesById(List<BankTransferReceipt> receipts, Map<Long, List<ReceiptSelectedInvoice>> selectionsByPaymentId) {
        Map<Long, Invoice> invoicesById = new HashMap<>();
        receipts.forEach(receipt -> invoicesById.put(receipt.getInvoice().getId(), receipt.getInvoice()));
        List<Long> missingIds = selectionsByPaymentId.values().stream().flatMap(Collection::stream).map(ReceiptSelectedInvoice::invoiceId)
                .filter(id -> !invoicesById.containsKey(id)).distinct().toList();
        if (!missingIds.isEmpty()) {
            invoiceRepository.findByIdIn(missingIds).forEach(invoice -> invoicesById.put(invoice.getId(), invoice));
        }
        return invoicesById;
    }

    private List<Long> paymentIdsOf(List<BankTransferReceipt> receipts) {
        return receipts.stream().map(BankTransferReceipt::getPaymentId).filter(Objects::nonNull).distinct().toList();
    }

    private Map<Long, List<Long>> invoiceIdsByPaymentId(List<BankTransferReceipt> receipts) {
        return receiptInvoiceIds.findInvoiceIdsByPaymentId(paymentIdsOf(receipts));
    }

    private BankTransferReceiptResponse assemble(BankTransferReceipt receipt, Map<Long, List<Long>> invoiceIdsByPaymentId, ReceiptReviewDetail detail) {
        Invoice invoice = receipt.getInvoice();
        String productName = invoice.findCourseEnrollmentId().flatMap(invoiceViewLoader::findCourseEnrollment).map(e -> e.getCourse().getName())
                .or(() -> invoice.findProgramEnrollmentId().flatMap(invoiceViewLoader::findProgramEnrollment).map(e -> e.getProgram().getName()))
                .orElse(null);
        List<Long> invoiceIds = receipt.getPaymentId() == null ? List.of(invoice.getId()) : invoiceIdsByPaymentId.getOrDefault(receipt.getPaymentId(), List.of(invoice.getId()));
        return BankTransferReceiptResponse.from(receipt, invoiceRepository.findOwnerPersonByInvoiceId(invoice.getId()).orElse(null), productName, invoiceIds, detail);
    }
}
