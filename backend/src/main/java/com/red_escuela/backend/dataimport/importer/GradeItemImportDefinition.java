package com.red_escuela.backend.dataimport.importer;

import com.red_escuela.backend.academicstructure.api.HistoricalSectionRecorder;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import com.red_escuela.backend.dataimport.specialist.ImportDocumentKey;
import com.red_escuela.backend.dataimport.specialist.ImportEntityDefinition;
import com.red_escuela.backend.dataimport.specialist.ImportReference;
import com.red_escuela.backend.dataimport.specialist.ImportRowReader;
import com.red_escuela.backend.dataimport.specialist.ImportValueParser;
import com.red_escuela.backend.evaluation.api.HistoricalGradeItem;
import com.red_escuela.backend.evaluation.api.HistoricalGradeItemRecorder;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GradeItemImportDefinition implements ImportEntityDefinition {
    public static final List<String> COLUMNS = List.of("documentType", "dni", "sectionCode", "type", "sequenceNumber",
            "date", "score");
    private static final Set<String> ACCEPTED = Set.of("EXAM", "TP");

    private final HistoricalGradeItemRecorder gradeItemRecorder;
    private final HistoricalSectionRecorder sectionRecorder;

    public GradeItemImportDefinition(HistoricalGradeItemRecorder gradeItemRecorder, HistoricalSectionRecorder sectionRecorder) {
        this.gradeItemRecorder = gradeItemRecorder;
        this.sectionRecorder = sectionRecorder;
    }

    @Override
    public ImportEntityType type() {
        return ImportEntityType.GRADE_ITEMS;
    }

    @Override
    public List<String> columns() {
        return COLUMNS;
    }

    @Override
    public String naturalKey(Map<String, String> payload) {
        return ImportDocumentKey.keyOf(payload) + "|" + ImportValueParser.cleanText(payload.get("sectionCode")) + "|"
                + ImportValueParser.cleanText(payload.get("type")).toUpperCase(Locale.ROOT) + "|"
                + ImportValueParser.cleanText(payload.get("sequenceNumber"));
    }

    @Override
    public Map<String, String> uniquenessValues(Map<String, String> payload) {
        return Map.of("gradeItem", naturalKey(payload));
    }

    @Override
    public List<ImportRowError> validateFormat(Map<String, String> payload) {
        ImportRowReader reader = new ImportRowReader(payload);
        if (ImportDocumentKey.typeOf(payload).isEmpty()) {
            reader.errors().add(new ImportRowError("documentType", "DOCUMENT_TYPE_INVALID", "El tipo de documento debe ser DNI, PASSPORT u OTHER"));
        }
        reader.requiredText("dni", 20);
        reader.requiredText("sectionCode", 60);
        String itemType = reader.requiredText("type", 10).toUpperCase(Locale.ROOT);
        if (!itemType.isEmpty() && !ACCEPTED.contains(itemType)) {
            reader.errors().add(new ImportRowError("type", "GRADE_ITEM_TYPE_NOT_ACCEPTED",
                    "El tipo debe ser EXAM o TP: la nota final va en el archivo de cursadas"));
        }
        reader.integer("sequenceNumber", true, 1, 50);
        LocalDate date = reader.date("date", true);
        if (date != null && date.isAfter(ApplicationTime.today())) {
            reader.errors().add(new ImportRowError("date", "DATE_IN_FUTURE", "La fecha es futura"));
        }
        BigDecimal score = reader.decimal("score", true);
        if (score != null && (score.compareTo(BigDecimal.ONE) < 0 || score.compareTo(BigDecimal.TEN) > 0
                || score.stripTrailingZeros().scale() > 2)) {
            reader.errors().add(new ImportRowError("score", "SCORE_OUT_OF_RANGE", "La nota va de 1 a 10 con hasta 2 decimales"));
        }
        return reader.errors();
    }

    @Override
    public List<ImportReference> references(Map<String, String> payload) {
        return List.of(ImportReference.byAlias(ImportEntityType.SECTION_ENROLLMENTS,
                SectionEnrollmentImportDefinition.studentSectionKey(ImportDocumentKey.keyOf(payload), payload.get("sectionCode")),
                "sectionCode", "SECTION_ENROLLMENT_NOT_FOUND"));
    }

    @Override
    public BaseCheck checkAgainstBase(Map<String, String> payload) {
        ImportRowReader reader = new ImportRowReader(payload);
        DocumentType type = ImportDocumentKey.typeOf(payload).orElseThrow();
        String itemType = reader.text("type").toUpperCase(Locale.ROOT);
        boolean exists = sectionRecorder.findSectionIdByCode(reader.text("sectionCode"))
                .map(sectionId -> gradeItemRecorder.gradeItemExists(type, ImportDocumentKey.numberOf(payload, type),
                        sectionId, itemType, reader.integer("sequenceNumber", true, 1, 50)))
                .orElse(false);
        List<ImportRowError> errors = new ArrayList<>();
        if (exists) {
            errors.add(new ImportRowError("sequenceNumber", "GRADE_ITEM_ALREADY_EXISTS", "El alumno ya tiene esa nota en la comisión"));
        }
        return new BaseCheck(exists, errors);
    }

    @Override
    public void discard(Long entityId) {
        gradeItemRecorder.discardGradeItem(entityId);
    }

    @Override
    public Long record(Map<String, String> payload, Long institutionId) {
        ImportRowReader reader = new ImportRowReader(payload);
        DocumentType type = ImportDocumentKey.typeOf(payload).orElseThrow();
        Long sectionId = sectionRecorder.findSectionIdByCode(reader.text("sectionCode")).orElseThrow();
        return gradeItemRecorder.recordGradeItem(new HistoricalGradeItem(type, ImportDocumentKey.numberOf(payload, type),
                sectionId, reader.text("type").toUpperCase(Locale.ROOT),
                reader.integer("sequenceNumber", true, 1, 50), reader.date("date", true), reader.decimal("score", true)));
    }
}
