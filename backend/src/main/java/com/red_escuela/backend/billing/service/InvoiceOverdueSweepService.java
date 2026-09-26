package com.red_escuela.backend.billing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.entity.BatchExecutionLog;
import com.red_escuela.backend.billing.entity.BatchExecutionStatus;
import com.red_escuela.backend.billing.entity.CollectionsPolicy;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.repository.BatchExecutionLogRepository;
import com.red_escuela.backend.billing.repository.CollectionsPolicyRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class InvoiceOverdueSweepService {
    private static final int PAGE_SIZE = 200;
    private static final String JOB_NAME = "InvoiceOverdueSweepService";

    private final InvoiceRepository invoiceRepository;
    private final CollectionsPolicyRepository collectionsPolicyRepository;
    private final BatchExecutionLogRepository batchExecutionLogRepository;
    private final InvoiceOverdueRowProcessor rowProcessor;
    private final ObjectMapper objectMapper;

    public InvoiceOverdueSweepService(InvoiceRepository invoiceRepository, CollectionsPolicyRepository collectionsPolicyRepository,
                                       BatchExecutionLogRepository batchExecutionLogRepository, InvoiceOverdueRowProcessor rowProcessor,
                                       ObjectMapper objectMapper) {
        this.invoiceRepository = invoiceRepository;
        this.collectionsPolicyRepository = collectionsPolicyRepository;
        this.batchExecutionLogRepository = batchExecutionLogRepository;
        this.rowProcessor = rowProcessor;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "${app.timezone}")
    public void run() {
        run(LocalDate.now());
    }

    public void run(LocalDate today) {
        Optional<CollectionsPolicy> policy = collectionsPolicyRepository.findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(today);
        if (policy.isEmpty()) {
            return;
        }
        long start = System.currentTimeMillis();
        int candidates = 0;
        int processed = 0;
        int failed = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        LocalDate cutoff = today.minusDays(policy.get().getGracePeriodDays());

        Long lastId = 0L;
        while (true) {
            List<Invoice> batch = invoiceRepository.findByStatusAndDueDateBeforeAndIdGreaterThanOrderByIdAsc(
                    InvoiceStatus.PENDING, cutoff, lastId, PageRequest.of(0, PAGE_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            candidates += batch.size();
            for (Invoice invoice : batch) {
                try {
                    rowProcessor.process(invoice.getId(), policy.get());
                    processed++;
                } catch (Exception e) {
                    failed++;
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("invoiceId", invoice.getId());
                    error.put("message", e.getMessage());
                    errors.add(error);
                }
            }
            lastId = batch.get(batch.size() - 1).getId();
        }

        long durationMs = System.currentTimeMillis() - start;
        BatchExecutionStatus status = failed == 0 ? BatchExecutionStatus.SUCCESS
                : (processed > 0 ? BatchExecutionStatus.PARTIAL_FAILURE : BatchExecutionStatus.FAILED);
        batchExecutionLogRepository.save(new BatchExecutionLog(
                JOB_NAME, LocalDateTime.now(), candidates, processed, failed, durationMs, status, serialize(errors)));
    }

    private String serialize(List<Map<String, Object>> errors) {
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (JsonProcessingException e) {
            return errors.toString();
        }
    }
}
