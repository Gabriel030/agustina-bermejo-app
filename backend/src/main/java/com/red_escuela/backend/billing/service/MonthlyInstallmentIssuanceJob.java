package com.red_escuela.backend.billing.service;

import java.time.Clock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.entity.BatchExecutionLog;
import com.red_escuela.backend.billing.entity.BatchExecutionStatus;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.repository.BatchExecutionLogRepository;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class MonthlyInstallmentIssuanceJob {
    private static final Logger log = LoggerFactory.getLogger(MonthlyInstallmentIssuanceJob.class);
    private static final int PAGE_SIZE = 200;
    static final int ISSUANCE_LAST_DAY = 5;
    private static final String JOB_NAME = "MonthlyInstallmentIssuanceJob";

    private final PaymentPlanSelectionRepository paymentPlanSelectionRepository;
    private final BatchExecutionLogRepository batchExecutionLogRepository;
    private final MonthlyInstallmentRowProcessor rowProcessor;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private BillingConfigurationAlertService alertService;
    private SuspendedEnrollmentClosureService closureService;

    @Autowired
    public MonthlyInstallmentIssuanceJob(PaymentPlanSelectionRepository paymentPlanSelectionRepository,
                                          BatchExecutionLogRepository batchExecutionLogRepository,
                                          MonthlyInstallmentRowProcessor rowProcessor, ObjectMapper objectMapper,
                                          MeterRegistry meterRegistry,
                                          Clock clock, BillingConfigurationAlertService alertService,
                                          SuspendedEnrollmentClosureService closureService) {
        this(paymentPlanSelectionRepository, batchExecutionLogRepository, rowProcessor, objectMapper, meterRegistry, clock);
        this.alertService = alertService;
        this.closureService = closureService;
    }

    public MonthlyInstallmentIssuanceJob(PaymentPlanSelectionRepository paymentPlanSelectionRepository,
                                          BatchExecutionLogRepository batchExecutionLogRepository,
                                          MonthlyInstallmentRowProcessor rowProcessor, ObjectMapper objectMapper,
                                          MeterRegistry meterRegistry,
                                          Clock clock) {
        this.paymentPlanSelectionRepository = paymentPlanSelectionRepository;
        this.batchExecutionLogRepository = batchExecutionLogRepository;
        this.rowProcessor = rowProcessor;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 4 1-5 * *", zone = "${app.timezone}")
    @SchedulerLock(name = "MonthlyInstallmentIssuanceJob", lockAtMostFor = "PT30M")
    public void run() {
        LocalDate today = LocalDate.now(clock);
        if (today.getDayOfMonth() > ISSUANCE_LAST_DAY) {
            log.info("Emisión mensual de cuotas omitida: el día {} está fuera de la ventana 1 a {}", today, ISSUANCE_LAST_DAY);
            return;
        }
        run(YearMonth.from(today));
    }

    public void run(YearMonth month) {
        log.info("Emisión mensual de cuotas iniciada: mes={}", month);
        long start = System.currentTimeMillis();
        Counters counters = new Counters();
        List<Map<String, Object>> errors = new ArrayList<>();

        List<ProgramEnrollmentStatus> activeStatuses = List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED);
        sweep("ProgramEnrollment",
                lastId -> paymentPlanSelectionRepository.findProgramEnrollmentIdsWithActivePlan(
                        PaymentPlanCadence.MONTHLY, activeStatuses, month, lastId, PageRequest.of(0, PAGE_SIZE)),
                id -> rowProcessor.issueForProgramEnrollment(id, month), counters, errors);
        sweep("ProgramEnrollmentWithoutMonthlyPlan",
                lastId -> paymentPlanSelectionRepository.findProgramEnrollmentIdsWithoutActivePlan(
                        PaymentPlanCadence.MONTHLY, activeStatuses, month, lastId, PageRequest.of(0, PAGE_SIZE)),
                id -> rowProcessor.issueForProgramEnrollment(id, month), counters, errors);
        sweep("CourseEnrollment",
                lastId -> paymentPlanSelectionRepository.findCourseEnrollmentIdsWithActivePlan(
                        PaymentPlanCadence.MONTHLY, EnrollmentStatus.ENROLLED, month, lastId, PageRequest.of(0, PAGE_SIZE)),
                id -> rowProcessor.issueForCourseEnrollment(id, month), counters, errors);

        closeSuspendedEnrollments();
        refreshAlerts(month);
        long durationMs = System.currentTimeMillis() - start;
        log.info("Emisión mensual de cuotas finalizada: mes={}, candidatas={}, emitidas={}, omitidas={}, fallidas={}, duracion={}ms",
                month, counters.candidates, counters.issued, counters.skipped, counters.failed, durationMs);
        meterRegistry.counter("billing_monthly_installments_issued_total").increment(counters.issued);
        meterRegistry.counter("billing_monthly_installments_failed_total").increment(counters.failed);
        int processed = counters.issued + counters.skipped;
        BatchExecutionStatus status = counters.failed == 0 ? BatchExecutionStatus.SUCCESS
                : (processed > 0 ? BatchExecutionStatus.PARTIAL_FAILURE : BatchExecutionStatus.FAILED);
        batchExecutionLogRepository.save(new BatchExecutionLog(
                JOB_NAME, LocalDateTime.now(clock), counters.candidates, processed, counters.failed, durationMs, status, serialize(errors)));
    }

    private void closeSuspendedEnrollments() {
        if (closureService == null) {
            return;
        }
        try {
            closureService.closeFinishedCycles();
        } catch (RuntimeException e) {
            log.error("Falló el cierre de inscripciones suspendidas al fin del año lectivo", e);
        }
    }

    private void refreshAlerts(YearMonth month) {
        if (alertService == null) {
            return;
        }
        try {
            alertService.refresh(month);
        } catch (RuntimeException e) {
            log.error("Falló la actualización de alertas de configuración de cobro: mes={}", month, e);
        }
    }

    private void sweep(String enrollmentType, Function<Long, List<Long>> nextPage,
                       Function<Long, InstallmentIssuanceOutcome> issue, Counters counters, List<Map<String, Object>> errors) {
        Long lastId = 0L;
        while (true) {
            List<Long> page = nextPage.apply(lastId);
            if (page.isEmpty()) {
                return;
            }
            counters.candidates += page.size();
            for (Long id : page) {
                try {
                    if (issue.apply(id) == InstallmentIssuanceOutcome.ISSUED) {
                        counters.issued++;
                    } else {
                        counters.skipped++;
                    }
                } catch (Exception e) {
                    counters.failed++;
                    log.error("Falló la emisión mensual de cuotas: tipo={}, id={}", enrollmentType, id, e);
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("enrollmentType", enrollmentType);
                    error.put("enrollmentId", id);
                    error.put("message", e.getMessage());
                    errors.add(error);
                }
            }
            lastId = page.get(page.size() - 1);
        }
    }

    private String serialize(List<Map<String, Object>> errors) {
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (JsonProcessingException e) {
            return errors.toString();
        }
    }

    private static final class Counters {
        private int candidates;
        private int issued;
        private int skipped;
        private int failed;
    }
}
