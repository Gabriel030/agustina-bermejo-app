package com.red_escuela.backend.billing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.entity.BatchExecutionLog;
import com.red_escuela.backend.billing.entity.BatchExecutionStatus;
import com.red_escuela.backend.billing.entity.CollectionsPolicy;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.billing.repository.BatchExecutionLogRepository;
import com.red_escuela.backend.billing.repository.CollectionsPolicyRepository;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
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

    @Scheduled(cron = "0 1 3 * * *", zone = "${app.timezone}")
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
                    errors.add(errorOf("ProgramEnrollment", enrollment.getId(), e));
                }
            }
            lastProgramEnrollmentId = batch.get(batch.size() - 1).getId();
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
