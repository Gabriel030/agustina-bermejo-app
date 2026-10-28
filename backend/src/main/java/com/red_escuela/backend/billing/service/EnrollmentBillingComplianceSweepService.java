package com.red_escuela.backend.billing.service;

import java.time.Clock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.collections.operation.CollectionsDailySnapshotStep;
import com.red_escuela.backend.billing.collections.operation.EnrollmentPaymentStandingStep;
import com.red_escuela.backend.billing.entity.BatchExecutionLog;
import com.red_escuela.backend.billing.entity.BatchExecutionStatus;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.billing.repository.BatchExecutionLogRepository;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
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
public class EnrollmentBillingComplianceSweepService {
    private static final Logger log = LoggerFactory.getLogger(EnrollmentBillingComplianceSweepService.class);
    private static final int PAGE_SIZE = 200;
    private static final String JOB_NAME = "EnrollmentBillingComplianceSweepService";

    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final BillingConfigurationReader configurationReader;
    private final BatchExecutionLogRepository batchExecutionLogRepository;
    private final EnrollmentBillingComplianceRowProcessor rowProcessor;
    private final CollectionsDailySnapshotStep snapshotStep;
    private final EnrollmentPaymentStandingStep standingStep;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EnrollmentBillingComplianceSweepService(CourseEnrollmentRepository courseEnrollmentRepository,
                                                     ProgramEnrollmentRepository programEnrollmentRepository,
                                                     BillingConfigurationReader configurationReader,
                                                     BatchExecutionLogRepository batchExecutionLogRepository,
                                                     EnrollmentBillingComplianceRowProcessor rowProcessor, CollectionsDailySnapshotStep snapshotStep,
                                                     EnrollmentPaymentStandingStep standingStep, ObjectMapper objectMapper, Clock clock) {
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.configurationReader = configurationReader;
        this.batchExecutionLogRepository = batchExecutionLogRepository;
        this.rowProcessor = rowProcessor;
        this.snapshotStep = snapshotStep;
        this.standingStep = standingStep;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(cron = "0 1 3 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "EnrollmentBillingComplianceSweepService", lockAtMostFor = "PT30M")
    public void run() {
        run(LocalDate.now(clock));
    }

    public void run(LocalDate today) {
        try {
            checkCompliance(today);
        } finally {
            takeCollectionsSnapshot(today);
        }
    }

    private void takeCollectionsSnapshot(LocalDate today) {
        try {
            snapshotStep.takeCollectionsSnapshot(today);
        } catch (RuntimeException e) {
            log.error("Falló la foto diaria de cobranza de la fecha {}", today, e);
        }
    }

    private void checkCompliance(LocalDate today) {
        Optional<LatePaymentTerms> policy = configurationReader.findLatePaymentSettingsEffectiveOn(today);
        if (policy.isEmpty()) {
            log.warn("Barrido de cumplimiento de facturación omitido: no hay política de cobranzas vigente al {}", today);
            return;
        }
        log.info("Barrido de cumplimiento de facturación iniciado: fecha={}", today);
        long start = System.currentTimeMillis();
        int candidates = 0;
        int processed = 0;
        int failed = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        Long lastCourseEnrollmentId = 0L;
        while (true) {
            List<CourseEnrollment> batch = courseEnrollmentRepository.findByStatusAndIdGreaterThanOrderByIdAsc(
                    EnrollmentStatus.ENROLLED, lastCourseEnrollmentId, PageRequest.of(0, PAGE_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            candidates += batch.size();
            for (CourseEnrollment enrollment : batch) {
                try {
                    rowProcessor.processCourseEnrollment(enrollment.getId(), policy.get(), today);
                    processed++;
                } catch (Exception e) {
                    failed++;
                    log.error("Falló el cumplimiento de facturación de la inscripción a curso id={}", enrollment.getId(), e);
                    errors.add(errorOf("CourseEnrollment", enrollment.getId(), e));
                }
            }
            lastCourseEnrollmentId = batch.get(batch.size() - 1).getId();
        }

        Long lastProgramEnrollmentId = 0L;
        List<ProgramEnrollmentStatus> activeStatuses = List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED);
        while (true) {
            List<ProgramEnrollment> batch = programEnrollmentRepository.findByStatusInAndIdGreaterThanOrderByIdAsc(
                    activeStatuses, lastProgramEnrollmentId, PageRequest.of(0, PAGE_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            candidates += batch.size();
            for (ProgramEnrollment enrollment : batch) {
                try {
                    rowProcessor.processProgramEnrollment(enrollment.getId(), policy.get(), today);
                    processed++;
                } catch (Exception e) {
                    failed++;
                    log.error("Falló el cumplimiento de facturación de la inscripción a carrera id={}", enrollment.getId(), e);
                    errors.add(errorOf("ProgramEnrollment", enrollment.getId(), e));
                }
            }
            lastProgramEnrollmentId = batch.get(batch.size() - 1).getId();
        }

        evaluateEnrollmentPaymentStanding(today, policy.get());
        long durationMs = System.currentTimeMillis() - start;
        log.info("Barrido de cumplimiento de facturación finalizado: candidatas={}, procesadas={}, fallidas={}, duracion={}ms",
                candidates, processed, failed, durationMs);
        BatchExecutionStatus status = failed == 0 ? BatchExecutionStatus.SUCCESS
                : (processed > 0 ? BatchExecutionStatus.PARTIAL_FAILURE : BatchExecutionStatus.FAILED);
        batchExecutionLogRepository.save(new BatchExecutionLog(
                JOB_NAME, LocalDateTime.now(clock), candidates, processed, failed, durationMs, status, serialize(errors)));
    }

    private void evaluateEnrollmentPaymentStanding(LocalDate today, LatePaymentTerms terms) {
        try {
            standingStep.evaluateEnrollments(today, terms);
        } catch (RuntimeException e) {
            log.error("Falló el estado de cobranza nuevo de la fecha {}", today, e);
        }
    }

    private Map<String, Object> errorOf(String enrollmentType, Long id, Exception e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("enrollmentType", enrollmentType);
        error.put("enrollmentId", id);
        error.put("message", e.getMessage());
        return error;
    }

    private String serialize(List<Map<String, Object>> errors) {
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (JsonProcessingException e) {
            return errors.toString();
        }
    }
}
