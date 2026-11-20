package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.entity.ImportBatch;
import com.red_escuela.backend.dataimport.entity.ImportBatchEvent;
import com.red_escuela.backend.dataimport.entity.ImportBatchEventType;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportFile;
import com.red_escuela.backend.dataimport.entity.ImportRow;
import com.red_escuela.backend.dataimport.exception.ImportBatchNotFoundException;
import com.red_escuela.backend.dataimport.exception.ImportBatchStateInvalidException;
import com.red_escuela.backend.dataimport.exception.ImportFileInvalidHeaderException;
import com.red_escuela.backend.dataimport.exception.ImportFileTooLargeException;
import com.red_escuela.backend.dataimport.repository.ImportBatchEventRepository;
import com.red_escuela.backend.dataimport.repository.ImportBatchRepository;
import com.red_escuela.backend.dataimport.repository.ImportFileRepository;
import com.red_escuela.backend.dataimport.repository.ImportRowRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ImportFileStager {
    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    public static final int MAX_ROWS_PER_FILE = 50_000;
    private static final int MAX_NATURAL_KEY = 255;

    private final ImportBatchRepository batchRepository;
    private final ImportFileRepository fileRepository;
    private final ImportRowRepository rowRepository;
    private final ImportBatchEventRepository eventRepository;
    private final ImportCsvParser parser;
    private final ImportDefinitionRegistry registry;

    public ImportFileStager(ImportBatchRepository batchRepository, ImportFileRepository fileRepository,
            ImportRowRepository rowRepository, ImportBatchEventRepository eventRepository, ImportCsvParser parser,
            ImportDefinitionRegistry registry) {
        this.batchRepository = batchRepository;
        this.fileRepository = fileRepository;
        this.rowRepository = rowRepository;
        this.eventRepository = eventRepository;
        this.parser = parser;
        this.registry = registry;
    }

    @Transactional
    public ImportFile stage(Long batchId, Long actorPersonId, ImportEntityType type, String originalName,
            byte[] content) {
        ImportBatch batch = batchRepository.findById(batchId).orElseThrow(() -> new ImportBatchNotFoundException(batchId));
        if (!batch.acceptsFiles()) {
            throw new ImportBatchStateInvalidException("subir archivos a", batch.getStatus().name());
        }
        if (content.length > MAX_FILE_BYTES) {
            throw new ImportFileTooLargeException(MAX_FILE_BYTES);
        }
        ImportEntityDefinition definition = registry.definitionFor(type);
        ImportCsvParser.ParsedCsv parsed = parser.parse(new ByteArrayInputStream(content), MAX_ROWS_PER_FILE);
        requireExactHeader(parsed.headers(), definition.columns());

        rowRepository.deleteByBatchIdAndEntityType(batchId, type);
        fileRepository.findByBatchIdAndEntityType(batchId, type).ifPresent(previous -> {
            fileRepository.delete(previous);
            fileRepository.flush();
        });

        List<ImportRow> rows = new ArrayList<>(parsed.rows().size());
        int rowNumber = 1;
        for (List<String> record : parsed.rows()) {
            rowNumber++;
            Map<String, String> payload = toPayload(parsed.headers(), record);
            String naturalKey = definition.naturalKey(payload);
            rows.add(new ImportRow(batchId, type, rowNumber,
                    naturalKey.length() > MAX_NATURAL_KEY ? naturalKey.substring(0, MAX_NATURAL_KEY) : naturalKey,
                    ImportHasher.contentHash(payload), payload));
        }
        rowRepository.saveAll(rows);

        LocalDateTime now = ApplicationTime.now();
        ImportFile file = fileRepository.save(new ImportFile(batchId, type,
                originalName.length() > MAX_NATURAL_KEY ? originalName.substring(0, MAX_NATURAL_KEY) : originalName, ImportHasher.sha256(content),
                content.length, rows.size(), now));
        batch.markFilesUploaded();
        eventRepository.save(new ImportBatchEvent(batchId, ImportBatchEventType.FILE_UPLOADED, actorPersonId, now,
                Map.of("entityType", type.name(), "rows", rows.size(), "sha256", file.getSha256())));
        return file;
    }

    private static Map<String, String> toPayload(List<String> headers, List<String> record) {
        Map<String, String> payload = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            payload.put(headers.get(i), i < record.size() ? ImportValueParser.cleanText(record.get(i)) : "");
        }
        return payload;
    }

    private static void requireExactHeader(List<String> headers, List<String> expected) {
        Set<String> actual = new HashSet<>(headers);
        if (actual.size() != headers.size()) {
            throw new ImportFileInvalidHeaderException("hay columnas repetidas");
        }
        List<String> missing = expected.stream().filter(column -> !actual.contains(column)).toList();
        if (!missing.isEmpty()) {
            throw new ImportFileInvalidHeaderException("faltan columnas " + missing);
        }
        List<String> unknown = headers.stream().filter(column -> !expected.contains(column)).toList();
        if (!unknown.isEmpty()) {
            throw new ImportFileInvalidHeaderException("columnas desconocidas " + unknown);
        }
    }
}
