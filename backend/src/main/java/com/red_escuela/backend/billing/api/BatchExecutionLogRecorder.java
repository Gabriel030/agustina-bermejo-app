package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.entity.BatchExecutionLog;
import com.red_escuela.backend.billing.entity.BatchExecutionStatus;
import com.red_escuela.backend.billing.repository.BatchExecutionLogRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class BatchExecutionLogRecorder {

    private final BatchExecutionLogRepository batchExecutionLogRepository;

    public BatchExecutionLogRecorder(BatchExecutionLogRepository batchExecutionLogRepository) {
        this.batchExecutionLogRepository = batchExecutionLogRepository;
    }

    public void recordRun(String jobName, LocalDateTime ranAt, int candidates, int processed, int failed,
                          long durationMs, String errors) {
        BatchExecutionStatus status = failed == 0 ? BatchExecutionStatus.SUCCESS
                : (processed > 0 ? BatchExecutionStatus.PARTIAL_FAILURE : BatchExecutionStatus.FAILED);
        batchExecutionLogRepository.save(new BatchExecutionLog(
                jobName, ranAt, candidates, processed, failed, durationMs, status, errors));
    }
}
