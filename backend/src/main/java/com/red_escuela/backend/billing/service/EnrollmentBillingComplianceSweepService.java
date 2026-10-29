package com.red_escuela.backend.billing.service;

import java.time.Clock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.collections.operation.CollectionsDailySnapshotStep;
import com.red_escuela.backend.billing.collections.operation.EnrollmentComplianceSweepPort;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.red_escuela.backend.billing.collections.domain.EnrollmentComplianceSweepReport;
import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EnrollmentBillingComplianceSweepService implements EnrollmentComplianceSweepPort {
    private static final Logger log = LoggerFactory.getLogger(EnrollmentBillingComplianceSweepService.class);
    public static final String NO_LATE_PAYMENT_POLICY = "NO_LATE_PAYMENT_POLICY";
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

    @Override
    public Optional<String> findNotApplicableCode(LocalDate today) {
        return configurationReader.findLatePaymentSettingsEffectiveOn(today).isEmpty() ? Optional.of(NO_LATE_PAYMENT_POLICY) : Optional.empty();
    }

    private void checkCompliance(LocalDate today) {
        if (findNotApplicableCode(today).isPresent()) {
            log.warn("Barrido de cumplimiento de facturación omitido: no hay política de cobranzas vigente al {}", today);
            return;
        }
        log.info("Barrido de cumplimiento de facturación iniciado: fecha={}", today);
        long start = System.currentTimeMillis();
        EnrollmentComplianceSweepReport outcome = sweepEnrollmentCompliance(today);
        long durationMs = System.currentTimeMillis() - start;
        log.info("Barrido de cumplimiento de facturación finalizado: candidatas={}, procesadas={}, fallidas={}, duracion={}ms",
                outcome.candidates(), outcome.processed(), outcome.failed(), durationMs);
        BatchExecutionStatus status = outcome.failed() == 0 ? BatchExecutionStatus.SUCCESS
                : (outcome.processed() > 0 ? BatchExecutionStatus.PARTIAL_FAILURE : BatchExecutionStatus.FAILED);
        List<Map<String, Object>> errors = outcome.failures().stream()
                .map(item -> errorOf(item.kind() == EnrollmentKind.COURSE ? "CourseEnrollment" : "ProgramEnrollment", item.enrollmentId(), item.message()))
                .toList();
        batchExecutionLogRepository.save(new BatchExecutionLog(
                JOB_NAME, LocalDateTime.now(clock), outcome.candidates(), outcome.processed(), outcome.failed(), durationMs, status, serialize(errors)));
    }

    @Override
    public EnrollmentComplianceSweepReport sweepEnrollmentCompliance(LocalDate today) {
        LatePaymentTerms policy = configurationReader.findLatePaymentSettingsEffectiveOn(today).orElseThrow();
        List<EnrollmentComplianceSweepReport.EnrollmentComplianceFailure> failures = new ArrayList<>();
        int candidates = 0;

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
                    rowProcessor.processCourseEnrollment(enrollment.getId(), policy, today);
                } catch (Exception e) {
                    log.error("Falló el cumplimiento de facturación de la inscripción a curso id={}", enrollment.getId(), e);
                    failures.add(new EnrollmentComplianceSweepReport.EnrollmentComplianceFailure(EnrollmentKind.COURSE, enrollment.getId(), e.getMessage()));
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
                    rowProcessor.processProgramEnrollment(enrollment.getId(), policy, today);
                } catch (Exception e) {
                    log.error("Falló el cumplimiento de facturación de la inscripción a carrera id={}", enrollment.getId(), e);
                    failures.add(new EnrollmentComplianceSweepReport.EnrollmentComplianceFailure(EnrollmentKind.PROGRAM, enrollment.getId(), e.getMessage()));
                }
            }
            lastProgramEnrollmentId = batch.get(batch.size() - 1).getId();
        }

        evaluateEnrollmentPaymentStanding(today, policy);
        return new EnrollmentComplianceSweepReport(candidates, failures);
    }

    private void evaluateEnrollmentPaymentStanding(LocalDate today, LatePaymentTerms terms) {
        try {
            standingStep.evaluateEnrollments(today, terms);
        } catch (RuntimeException e) {
            log.error("Falló el estado de cobranza nuevo de la fecha {}", today, e);
        }
    }

    private Map<String, Object> errorOf(String enrollmentType, Long id, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("enrollmentType", enrollmentType);
        error.put("enrollmentId", id);
        error.put("message", message);
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
