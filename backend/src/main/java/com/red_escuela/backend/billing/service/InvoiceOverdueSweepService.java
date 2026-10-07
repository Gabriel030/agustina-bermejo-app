package com.red_escuela.backend.billing.service;

import java.time.Clock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.entity.BatchExecutionLog;
import com.red_escuela.backend.billing.entity.BatchExecutionStatus;
import com.red_escuela.backend.billing.entity.LatePaymentSettings;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.repository.BatchExecutionLogRepository;
import com.red_escuela.backend.billing.repository.LatePaymentSettingsRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(InvoiceOverdueSweepService.class);
    private static final int PAGE_SIZE = 200;
    private static final String JOB_NAME = "InvoiceOverdueSweepService";

    private final InvoiceRepository invoiceRepository;
    private final LatePaymentSettingsRepository latePaymentSettingsRepository;
    private final BatchExecutionLogRepository batchExecutionLogRepository;
    private final InvoiceOverdueRowProcessor rowProcessor;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InvoiceOverdueSweepService(InvoiceRepository invoiceRepository, LatePaymentSettingsRepository latePaymentSettingsRepository,
                                       BatchExecutionLogRepository batchExecutionLogRepository, InvoiceOverdueRowProcessor rowProcessor,
                                       ObjectMapper objectMapper,
                                       Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.latePaymentSettingsRepository = latePaymentSettingsRepository;
        this.batchExecutionLogRepository = batchExecutionLogRepository;
        this.rowProcessor = rowProcessor;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "InvoiceOverdueSweepService", lockAtMostFor = "PT30M")
    public void run() {
        run(LocalDate.now(clock));
    }

    public void run(LocalDate today) {
        Optional<LatePaymentSettings> policy = latePaymentSettingsRepository.findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(today);
        if (policy.isEmpty()) {
            log.warn("Barrido de mora omitido: no hay política de cobranzas vigente al {}", today);
            return;
        }
        log.info("Barrido de mora iniciado: fecha={}", today);
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
                    log.error("Falló el barrido de mora para la factura id={}", invoice.getId(), e);
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("invoiceId", invoice.getId());
                    error.put("message", e.getMessage());
                    errors.add(error);
                }
            }
            lastId = batch.get(batch.size() - 1).getId();
        }

        long durationMs = System.currentTimeMillis() - start;
        log.info("Barrido de mora finalizado: candidatas={}, procesadas={}, fallidas={}, duracion={}ms",
                candidates, processed, failed, durationMs);
        BatchExecutionStatus status = failed == 0 ? BatchExecutionStatus.SUCCESS
                : (processed > 0 ? BatchExecutionStatus.PARTIAL_FAILURE : BatchExecutionStatus.FAILED);
        batchExecutionLogRepository.save(new BatchExecutionLog(
                JOB_NAME, LocalDateTime.now(clock), candidates, processed, failed, durationMs, status, serialize(errors)));
    }

    private String serialize(List<Map<String, Object>> errors) {
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (JsonProcessingException e) {
            return errors.toString();
        }
    }
}
