package com.red_escuela.backend.billing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualResult;
import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualStep;
import com.red_escuela.backend.billing.collections.operation.OverdueDetectionResult;
import com.red_escuela.backend.billing.collections.operation.OverdueInvoiceDetectionStep;
import com.red_escuela.backend.billing.entity.BatchExecutionLog;
import com.red_escuela.backend.billing.entity.BatchExecutionStatus;
import com.red_escuela.backend.billing.repository.BatchExecutionLogRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class InvoiceOverdueSweepService {
    private static final Logger log = LoggerFactory.getLogger(InvoiceOverdueSweepService.class);
    private static final String JOB_NAME = "InvoiceOverdueSweepService";

    private final OverdueInvoiceDetectionStep detectionStep;
    private final LateFeeAccrualStep accrualStep;
    private final BatchExecutionLogRepository batchExecutionLogRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InvoiceOverdueSweepService(OverdueInvoiceDetectionStep detectionStep, LateFeeAccrualStep accrualStep,
                                       BatchExecutionLogRepository batchExecutionLogRepository, ObjectMapper objectMapper, Clock clock) {
        this.detectionStep = detectionStep;
        this.accrualStep = accrualStep;
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
        OverdueDetectionResult detection = detectionStep.detectOverdueInvoices(today);
        LateFeeAccrualResult accrual = accrualStep.accrueLateFees(today);
        long durationMs = System.currentTimeMillis() - start;
        log.info("Barrido de mora finalizado: candidatas={}, procesadas={}, omitidas={}, fallidas={}, recargosCandidatas={}, recargosPagoCandidatas={}, recargosCierre={}, recargosPagoRecuperados={}, recargosFallidos={}, duracion={}ms",
                detection.candidates(), detection.marked(), detection.skipped(), detection.failed(), accrual.candidates(), accrual.settlementCandidates(), accrual.debitNotesIssued() - accrual.settlementDebitNotesRecovered(), accrual.settlementDebitNotesRecovered(), accrual.failed(),
                durationMs);
        int failed = detection.failed() + detection.skipped() + accrual.failed();
        int processed = detection.marked() + accrual.debitNotesIssued();
        BatchExecutionStatus status = failed == 0 ? BatchExecutionStatus.SUCCESS
                : (processed > 0 || detection.failed() + accrual.failed() == 0 ? BatchExecutionStatus.PARTIAL_FAILURE : BatchExecutionStatus.FAILED);
        List<Map<String, Object>> errors = new ArrayList<>(detection.errors());
        errors.addAll(accrual.errors());
        batchExecutionLogRepository.save(new BatchExecutionLog(
                JOB_NAME, LocalDateTime.now(clock), detection.candidates() + accrual.candidates() + accrual.settlementCandidates(), processed, failed, durationMs, status, serialize(errors)));
    }

    private String serialize(List<Map<String, Object>> errors) {
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (JsonProcessingException e) {
            return errors.toString();
        }
    }
}
