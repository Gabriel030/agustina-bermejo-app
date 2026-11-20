package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.dataimport.dto.ImportFileDownload;
import com.red_escuela.backend.dataimport.entity.ImportRow;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import com.red_escuela.backend.dataimport.exception.ImportBatchNotFoundException;
import com.red_escuela.backend.dataimport.repository.ImportBatchRepository;
import com.red_escuela.backend.dataimport.repository.ImportRowRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ImportReportWriter {
    private static final String BOM = "﻿";
    private static final String HEADER = "entityType,rowNumber,naturalKey,status,errorColumn,errorCode,errorMessage,createdEntityId";
    private static final int PAGE_SIZE = 1000;

    private final ImportBatchRepository batchRepository;
    private final ImportRowRepository rowRepository;
    private final AuditTrail auditTrail;

    public ImportReportWriter(ImportBatchRepository batchRepository, ImportRowRepository rowRepository,
            AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
    }

    @Transactional
    public ImportFileDownload write(Long batchId) {
        batchRepository.findById(batchId).orElseThrow(() -> new ImportBatchNotFoundException(batchId));
        StringBuilder csv = new StringBuilder(BOM).append(HEADER).append("\r\n");
        int page = 0;
        List<ImportRow> rows = rowRepository.findByBatchIdOrderByEntityTypeAscRowNumberAsc(batchId, PageRequest.of(page, PAGE_SIZE));
        while (!rows.isEmpty()) {
            rows.forEach(row -> appendRow(csv, row));
            rows = rowRepository.findByBatchIdOrderByEntityTypeAscRowNumberAsc(batchId, PageRequest.of(++page, PAGE_SIZE));
        }
        auditTrail.record(new AuditRecord(AuditAction.DATA_IMPORT_REPORT_DOWNLOADED, AuditEntityType.DATA_IMPORT_BATCH, batchId,
                null, null, null));
        return new ImportFileDownload("reporte-importacion-" + batchId + ".csv", csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void appendRow(StringBuilder csv, ImportRow row) {
        List<ImportRowError> errors = row.getErrors() == null ? List.of() : row.getErrors();
        if (errors.isEmpty()) {
            csv.append(line(row, null));
            return;
        }
        errors.forEach(error -> csv.append(line(row, error)));
    }

    private String line(ImportRow row, ImportRowError error) {
        return List.of(row.getEntityType().name(), String.valueOf(row.getRowNumber()), row.getNaturalKey(),
                row.getStatus().name(), error == null ? "" : orEmpty(error.column()),
                error == null ? "" : orEmpty(error.code()), error == null ? "" : orEmpty(error.message()),
                row.getCommittedEntityId() == null ? "" : row.getCommittedEntityId().toString())
                .stream().map(ImportReportWriter::cell).collect(Collectors.joining(",")) + "\r\n";
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    static String cell(String value) {
        String safe = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
