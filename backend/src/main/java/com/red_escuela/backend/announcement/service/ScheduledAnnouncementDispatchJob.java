package com.red_escuela.backend.announcement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementStatus;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.billing.entity.BatchExecutionLog;
import com.red_escuela.backend.billing.entity.BatchExecutionStatus;
import com.red_escuela.backend.billing.repository.BatchExecutionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScheduledAnnouncementDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledAnnouncementDispatchJob.class);
    private static final String JOB_NAME = "ScheduledAnnouncementDispatchJob";
    private static final int PAGE_SIZE = 100;

    private final AnnouncementRepository announcementRepository;
    private final ScheduledAnnouncementRowProcessor rowProcessor;
    private final BatchExecutionLogRepository batchExecutionLogRepository;
    private final ObjectMapper objectMapper;

    public ScheduledAnnouncementDispatchJob(AnnouncementRepository announcementRepository,
                                            ScheduledAnnouncementRowProcessor rowProcessor,
                                            BatchExecutionLogRepository batchExecutionLogRepository,
                                            ObjectMapper objectMapper) {
        this.announcementRepository = announcementRepository;
        this.rowProcessor = rowProcessor;
        this.batchExecutionLogRepository = batchExecutionLogRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "${app.timezone}")
    public void run() {
        dispatchDue(LocalDateTime.now());
    }

    public void dispatchDue(LocalDateTime now) {
        long start = System.currentTimeMillis();
        int candidates = 0;
        int processed = 0;
        int failed = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        Long lastId = 0L;
        while (true) {
            List<Announcement> batch = announcementRepository
                    .findByStatusAndScheduledForLessThanEqualAndIdGreaterThanOrderByIdAsc(
                            AnnouncementStatus.SCHEDULED, now, lastId, PageRequest.of(0, PAGE_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            candidates += batch.size();
            for (Announcement announcement : batch) {
                try {
                    int recipients = rowProcessor.process(announcement.getId(), now);
                    processed++;
                    log.info("Aviso programado enviado: id={}, comision={}/{}, segmento={}, programado={}, destinatarios={}",
                            announcement.getId(), announcement.getSectionType(), announcement.getSectionId(),
                            announcement.getAudienceSpec().getKey(), announcement.getScheduledFor(), recipients);
                } catch (Exception e) {
                    failed++;
                    log.error("Falló el envío del aviso programado id={}: {}", announcement.getId(), e.getMessage(), e);
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("announcementId", announcement.getId());
                    error.put("message", e.getMessage());
                    errors.add(error);
                }
            }
            lastId = batch.get(batch.size() - 1).getId();
        }

        if (candidates == 0) {
            return;
        }

        long durationMs = System.currentTimeMillis() - start;
        BatchExecutionStatus status = failed == 0 ? BatchExecutionStatus.SUCCESS
                : (processed > 0 ? BatchExecutionStatus.PARTIAL_FAILURE : BatchExecutionStatus.FAILED);
        log.info("Envío programado de avisos: vencidos={}, enviados={}, fallidos={}, duracion={}ms",
                candidates, processed, failed, durationMs);
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
