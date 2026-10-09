package com.red_escuela.backend.billing.service;

import java.time.Clock;
import com.red_escuela.backend.billing.collections.operation.OverdueDetectionResult;
import com.red_escuela.backend.billing.collections.operation.OverdueInvoiceDetectionStep;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.entity.BatchExecutionLog;
import com.red_escuela.backend.billing.entity.BatchExecutionStatus;
import com.red_escuela.backend.billing.repository.BatchExecutionLogRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class InvoiceOverdueSweepService {
    private static final Logger log = LoggerFactory.getLogger(InvoiceOverdueSweepService.class);
    private static final int PAGE_SIZE = 200;
    private static final String JOB_NAME = "InvoiceOverdueSweepService";

    private final OverdueInvoiceDetectionStep detectionStep;
    private final BatchExecutionLogRepository batchExecutionLogRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InvoiceOverdueSweepService(OverdueInvoiceDetectionStep detectionStep, BatchExecutionLogRepository batchExecutionLogRepository,
                                       ObjectMapper objectMapper, Clock clock) {
        this.detectionStep = detectionStep;
        this.batchExecutionLogRepository = batchExecutionLogRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "InvoiceOverdueSweepService", lockAtMostFor = "PT30M")
    public void run() {
        run(LocalDate.now(clock));
    }

    public void run(LocalDate today) {
        log.info("Barrido de mora iniciado: fecha={}", today);
        long start = System.currentTimeMillis();
        OverdueDetectionResult result = detectionStep.detectOverdueInvoices(today);
        long durationMs = System.currentTimeMillis() - start;
        log.info("Barrido de mora finalizado: candidatas={}, procesadas={}, fallidas={}, duracion={}ms",
                result.candidates(), result.marked(), result.failed(), durationMs);
        BatchExecutionStatus status = result.failed() == 0 ? BatchExecutionStatus.SUCCESS
                : (result.marked() > 0 ? BatchExecutionStatus.PARTIAL_FAILURE : BatchExecutionStatus.FAILED);
        batchExecutionLogRepository.save(new BatchExecutionLog(
                JOB_NAME, LocalDateTime.now(clock), result.candidates(), result.marked(), result.failed(), durationMs, status, serialize(result.errors())));
    }

    private String serialize(List<Map<String, Object>> errors) {
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (JsonProcessingException e) {
            return errors.toString();
        }
    }
}
