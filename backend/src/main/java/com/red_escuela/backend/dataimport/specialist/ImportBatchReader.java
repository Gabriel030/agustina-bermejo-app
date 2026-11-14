package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;
import com.red_escuela.backend.dataimport.dto.ImportBatchResponse.ImportBlockCounters;
import com.red_escuela.backend.dataimport.dto.ImportRowErrorResponse;
import com.red_escuela.backend.dataimport.entity.ImportBatch;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportRow;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import com.red_escuela.backend.dataimport.entity.ImportRowStatus;
import com.red_escuela.backend.dataimport.exception.ImportBatchNotFoundException;
import com.red_escuela.backend.dataimport.repository.ImportBatchRepository;
import com.red_escuela.backend.dataimport.repository.ImportFileRepository;
import com.red_escuela.backend.dataimport.repository.ImportRowRepository;
import com.red_escuela.backend.dataimport.repository.ImportRowStatusCount;
import com.red_escuela.backend.shared.pagination.PageResponse;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ImportBatchReader {
    private static final List<ImportRowStatus> WITH_ISSUES = List.of(ImportRowStatus.ERROR, ImportRowStatus.CONFLICT,
            ImportRowStatus.FAILED);

    private final ImportBatchRepository batchRepository;
    private final ImportFileRepository fileRepository;
    private final ImportRowRepository rowRepository;

    public ImportBatchReader(ImportBatchRepository batchRepository, ImportFileRepository fileRepository,
            ImportRowRepository rowRepository) {
        this.batchRepository = batchRepository;
        this.fileRepository = fileRepository;
        this.rowRepository = rowRepository;
    }

    public ImportBatchResponse get(Long batchId) {
        return describe(batchRepository.findById(batchId).orElseThrow(() -> new ImportBatchNotFoundException(batchId)));
    }

    public List<ImportBatchResponse> list() {
        return batchRepository.findAllByOrderByIdDesc().stream().map(this::describe).toList();
    }

    public PageResponse<ImportRowErrorResponse> errors(Long batchId, int page, int size) {
        batchRepository.findById(batchId).orElseThrow(() -> new ImportBatchNotFoundException(batchId));
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), PageResponse.MAX_SIZE);
        long total = rowRepository.countByBatchIdAndStatusIn(batchId, WITH_ISSUES);
        List<ImportRowErrorResponse> items = new ArrayList<>();
        for (ImportRow row : rowRepository.findByBatchIdAndStatusInOrderByEntityTypeAscRowNumberAsc(batchId,
                WITH_ISSUES, PageRequest.of(boundedPage, boundedSize))) {
            for (ImportRowError error : row.getErrors()) {
                items.add(new ImportRowErrorResponse(row.getEntityType(), row.getRowNumber(), row.getStatus(),
                        error.column(), error.code(), error.message()));
            }
        }
        return new PageResponse<>(items, boundedPage, boundedSize, total);
    }

    private ImportBatchResponse describe(ImportBatch batch) {
        Map<ImportEntityType, Map<ImportRowStatus, Long>> grouped = new EnumMap<>(ImportEntityType.class);
        for (ImportRowStatusCount count : rowRepository.countByStatus(batch.getId())) {
            grouped.computeIfAbsent(count.entityType(), type -> new EnumMap<>(ImportRowStatus.class))
                    .put(count.status(), count.total());
        }
        List<ImportBlockCounters> counters = grouped.entrySet().stream()
                .map(entry -> new ImportBlockCounters(entry.getKey(), entry.getValue())).toList();
        return ImportBatchResponse.from(batch, fileRepository.findByBatchIdOrderByEntityType(batch.getId()), counters);
    }
}
