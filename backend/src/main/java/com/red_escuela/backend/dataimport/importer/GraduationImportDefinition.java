package com.red_escuela.backend.dataimport.importer;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import com.red_escuela.backend.dataimport.specialist.ImportDocumentKey;
import com.red_escuela.backend.dataimport.specialist.ImportEntityDefinition;
import com.red_escuela.backend.dataimport.specialist.ImportReference;
import com.red_escuela.backend.dataimport.specialist.ImportRowReader;
import com.red_escuela.backend.dataimport.specialist.ImportValueParser;
import com.red_escuela.backend.graduation.api.HistoricalGraduation;
import com.red_escuela.backend.graduation.api.HistoricalGraduationRecorder;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GraduationImportDefinition implements ImportEntityDefinition {
    public static final List<String> COLUMNS = List.of("documentType", "dni", "programName", "graduationDate",
            "registryNumber", "legacyTitleNumber");
    private static final int REGISTRY_NUMBER_MAX_LENGTH = 20;
    private static final int LEGACY_TITLE_NUMBER_MAX_LENGTH = 50;

    private final HistoricalGraduationRecorder recorder;

    public GraduationImportDefinition(HistoricalGraduationRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public ImportEntityType type() {
        return ImportEntityType.GRADUATIONS;
    }

    @Override
    public List<String> columns() {
        return COLUMNS;
    }

    @Override
    public String naturalKey(Map<String, String> payload) {
        return ProgramEnrollmentImportDefinition.keyOf(ImportDocumentKey.keyOf(payload), payload.get("programName"));
    }

    @Override
    public Map<String, String> uniquenessValues(Map<String, String> payload) {
        return Map.of("studentProgramGraduation", naturalKey(payload));
    }

    @Override
    public List<ImportRowError> validateFormat(Map<String, String> payload) {
        ImportRowReader reader = new ImportRowReader(payload);
        if (ImportDocumentKey.typeOf(payload).isEmpty()) {
            reader.errors().add(new ImportRowError("documentType", "DOCUMENT_TYPE_INVALID", "El tipo de documento debe ser DNI, PASSPORT u OTHER"));
        }
        reader.requiredText("dni", 20);
        reader.requiredText("programName", 255);
        LocalDate graduationDate = reader.date("graduationDate", true);
        if (graduationDate != null && graduationDate.isAfter(ApplicationTime.today())) {
            reader.errors().add(new ImportRowError("graduationDate", "GRADUATION_DATE_IN_FUTURE", "La fecha de egreso es futura"));
        }
        requireMaxLength(reader, "registryNumber", REGISTRY_NUMBER_MAX_LENGTH);
        requireMaxLength(reader, "legacyTitleNumber", LEGACY_TITLE_NUMBER_MAX_LENGTH);
        return reader.errors();
    }

    private void requireMaxLength(ImportRowReader reader, String column, int maxLength) {
        if (reader.text(column).length() > maxLength) {
            reader.errors().add(new ImportRowError(column, column.toUpperCase(Locale.ROOT) + "_TOO_LONG",
                    "El texto supera " + maxLength + " caracteres"));
        }
    }

    @Override
    public List<ImportReference> references(Map<String, String> payload) {
        LocalDate graduationDate = ImportValueParser.parseDateOrEmpty(payload.get("graduationDate")).orElse(null);
        if (graduationDate == null) {
            return List.of();
        }
        return List.of(ImportReference.byAlias(ImportEntityType.PROGRAM_ENROLLMENTS,
                ProgramEnrollmentImportDefinition.completedAlias(ImportDocumentKey.keyOf(payload), payload.get("programName"), graduationDate),
                "graduationDate", "COMPLETED_PROGRAM_ENROLLMENT_NOT_FOUND"));
    }

    @Override
    public BaseCheck checkAgainstBase(Map<String, String> payload) {
        boolean exists = existsInBase(naturalKey(payload));
        return new BaseCheck(exists, exists
                ? List.of(new ImportRowError("programName", "GRADUATION_ALREADY_EXISTS", "El alumno ya tiene un egreso en la carrera"))
                : List.of());
    }

    @Override
    public boolean existsInBase(String naturalKey) {
        String[] parts = naturalKey.split("\\|", 2);
        if (parts.length != 2) {
            return false;
        }
        return recorder.graduationExists(ImportDocumentKey.typeOfKey(parts[0]), ImportDocumentKey.numberOfKey(parts[0]), parts[1]);
    }

    @Override
    public void discard(Long entityId) {
        recorder.discardGraduation(entityId);
    }

    @Override
    public Long record(Map<String, String> payload, Long institutionId) {
        ImportRowReader reader = new ImportRowReader(payload);
        DocumentType type = ImportDocumentKey.typeOf(payload).orElseThrow();
        return recorder.recordGraduation(new HistoricalGraduation(type, ImportDocumentKey.numberOf(payload, type),
                reader.text("programName"), reader.date("graduationDate", true), blankToNull(reader.text("registryNumber")),
                blankToNull(reader.text("legacyTitleNumber"))));
    }

    private static String blankToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
