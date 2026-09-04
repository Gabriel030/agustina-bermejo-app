package com.red_escuela.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.entity.BatchExecutionLog;
import com.red_escuela.backend.entity.BatchExecutionStatus;
import com.red_escuela.backend.entity.CollectionsPolicy;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.repository.BatchExecutionLogRepository;
import com.red_escuela.backend.repository.CollectionsPolicyRepository;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
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

// Corre después de InvoiceOverdueSweepService (mismo cron diario, minuto siguiente): a
// diferencia de ese sweep, la paginación acá SÍ puede ser OFFSET normal — filtra por status
// académico y solo modifica billingStanding, un campo distinto del que usa el WHERE, así que no
// hay mutación del filtro propio a mitad de corrida.
@Service
public class EnrollmentBillingComplianceSweepService {

    private static final int PAGE_SIZE = 200;
    private static final String JOB_NAME = "EnrollmentBillingComplianceSweepService";

    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CollectionsPolicyRepository collectionsPolicyRepository;
    private final BatchExecutionLogRepository batchExecutionLogRepository;
    private final EnrollmentBillingComplianceRowProcessor rowProcessor;
    private final ObjectMapper objectMapper;

    public EnrollmentBillingComplianceSweepService(CourseEnrollmentRepository courseEnrollmentRepository,
                                                     ProgramEnrollmentRepository programEnrollmentRepository,
                                                     CollectionsPolicyRepository collectionsPolicyRepository,
                                                     BatchExecutionLogRepository batchExecutionLogRepository,
                                                     EnrollmentBillingComplianceRowProcessor rowProcessor, ObjectMapper objectMapper) {
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.collectionsPolicyRepository = collectionsPolicyRepository;
        this.batchExecutionLogRepository = batchExecutionLogRepository;
        this.rowProcessor = rowProcessor;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "0 1 3 * * *")
    public void run() {
        run(LocalDate.now());
    }

    public void run(LocalDate today) {
        Optional<CollectionsPolicy> policy = collectionsPolicyRepository.findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(today);
        if (policy.isEmpty()) {
            return;
        }
        long start = System.currentTimeMillis();
        int candidates = 0;
        int processed = 0;
        int failed = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        int page = 0;
        while (true) {
            List<CourseEnrollment> batch = courseEnrollmentRepository.findByStatusOrderByIdAsc(EnrollmentStatus.ENROLLED, PageRequest.of(page, PAGE_SIZE));
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
                    errors.add(errorOf("CourseEnrollment", enrollment.getId(), e));
                }
            }
            page++;
        }

        page = 0;
        List<ProgramEnrollmentStatus> activeStatuses = List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED);
        while (true) {
            List<ProgramEnrollment> batch = programEnrollmentRepository.findByStatusInOrderByIdAsc(activeStatuses, PageRequest.of(page, PAGE_SIZE));
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
                    errors.add(errorOf("ProgramEnrollment", enrollment.getId(), e));
                }
            }
            page++;
        }

        long durationMs = System.currentTimeMillis() - start;
        BatchExecutionStatus status = failed == 0 ? BatchExecutionStatus.SUCCESS
                : (processed > 0 ? BatchExecutionStatus.PARTIAL_FAILURE : BatchExecutionStatus.FAILED);
        batchExecutionLogRepository.save(new BatchExecutionLog(
                JOB_NAME, LocalDateTime.now(), candidates, processed, failed, durationMs, status, serialize(errors)));
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
