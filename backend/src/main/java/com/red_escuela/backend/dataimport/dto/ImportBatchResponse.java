package com.red_escuela.backend.dataimport.dto;

import com.red_escuela.backend.dataimport.entity.ImportBatch;
import com.red_escuela.backend.dataimport.entity.ImportBatchStatus;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportFile;
import com.red_escuela.backend.dataimport.entity.ImportRowStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ImportBatchResponse(
        Long id,
        String name,
        ImportBatchStatus status,
        LocalDateTime createdAt,
        LocalDateTime validatedAt,
        LocalDateTime committedAt,
        List<ImportFileResponse> files,
        List<ImportBlockCounters> counters) {

    public record ImportFileResponse(ImportEntityType entityType, String originalName, int rowCount, long sizeBytes,
            LocalDateTime uploadedAt) {
        public static ImportFileResponse from(ImportFile file) {
            return new ImportFileResponse(file.getEntityType(), file.getOriginalName(), file.getRowCount(),
                    file.getSizeBytes(), file.getUploadedAt());
        }
    }

    public record ImportBlockCounters(ImportEntityType entityType, Map<ImportRowStatus, Long> rowsByStatus) {
    }

    public static ImportBatchResponse from(ImportBatch batch, List<ImportFile> files, List<ImportBlockCounters> counters) {
        return new ImportBatchResponse(batch.getId(), batch.getName(), batch.getStatus(), batch.getCreatedAt(),
                batch.getValidatedAt(), batch.getCommittedAt(), files.stream().map(ImportFileResponse::from).toList(),
                counters);
    }
}
