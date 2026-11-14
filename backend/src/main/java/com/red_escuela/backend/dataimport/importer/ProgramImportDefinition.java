package com.red_escuela.backend.dataimport.importer;

import com.red_escuela.backend.academicstructure.api.HistoricalProgram;
import com.red_escuela.backend.academicstructure.api.HistoricalProgramRecorder;
import com.red_escuela.backend.academicstructure.entity.ProgramLevel;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import com.red_escuela.backend.dataimport.specialist.ImportEntityDefinition;
import com.red_escuela.backend.dataimport.specialist.ImportRowReader;
import com.red_escuela.backend.dataimport.specialist.ImportValueParser;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProgramImportDefinition implements ImportEntityDefinition {
    public static final List<String> COLUMNS = List.of("programName", "level", "totalYears", "totalCreditsRequired",
            "totalHours", "teachingMode", "status", "listPrice", "listEnrollmentFee", "currency", "graduationProfile");

    private final HistoricalProgramRecorder recorder;

    public ProgramImportDefinition(HistoricalProgramRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public ImportEntityType type() {
        return ImportEntityType.PROGRAMS;
    }

    @Override
    public List<String> columns() {
        return COLUMNS;
    }

    @Override
    public String naturalKey(Map<String, String> payload) {
        return ImportValueParser.cleanText(payload.get("programName"));
    }

    @Override
    public Map<String, String> uniquenessValues(Map<String, String> payload) {
        return Map.of("programName", naturalKey(payload));
    }

    @Override
    public List<ImportRowError> validateFormat(Map<String, String> payload) {
        ImportRowReader reader = new ImportRowReader(payload);
        reader.requiredText("programName", 255);
        reader.enumValue("level", ProgramLevel.class, false);
        reader.integer("totalYears", false, 1, 15);
        reader.integer("totalCreditsRequired", true, 1, 100000);
        reader.integer("totalHours", false, 1, 100000);
        reader.enumValue("teachingMode", TeachingMode.class, false);
        validateStatus(reader);
        reader.enumValue("currency", Currency.class, false);
        requirePositive(reader, "listPrice");
        requirePositive(reader, "listEnrollmentFee");
        return reader.errors();
    }

    @Override
    public BaseCheck checkAgainstBase(Map<String, String> payload) {
        boolean exists = recorder.programExists(naturalKey(payload));
        return new BaseCheck(exists, exists
                ? List.of(new ImportRowError("programName", "PROGRAM_ALREADY_EXISTS", "Ya existe una carrera con ese nombre"))
                : List.of());
    }

    @Override
    public boolean existsInBase(String naturalKey) {
        return recorder.programExists(naturalKey);
    }

    @Override
    public Long record(Map<String, String> payload, Long institutionId) {
        ImportRowReader reader = new ImportRowReader(payload);
        Currency currency = reader.enumValue("currency", Currency.class, false).orElse(Currency.ARS);
        HistoricalProgram program = new HistoricalProgram(reader.text("programName"),
                reader.enumValue("level", ProgramLevel.class, false).orElse(null),
                reader.integer("totalYears", false, 1, 15), reader.integer("totalCreditsRequired", true, 1, 100000),
                reader.integer("totalHours", false, 1, 100000),
                reader.enumValue("teachingMode", TeachingMode.class, false).orElse(null),
                !"INACTIVE".equalsIgnoreCase(reader.text("status")),
                Money.of(reader.decimal("listPrice", true), currency),
                Money.of(reader.decimal("listEnrollmentFee", true), currency),
                reader.text("graduationProfile").isEmpty() ? null : reader.text("graduationProfile"));
        return recorder.recordProgram(program);
    }

    private static void validateStatus(ImportRowReader reader) {
        String status = reader.text("status");
        if (!status.equalsIgnoreCase("ACTIVE") && !status.equalsIgnoreCase("INACTIVE")) {
            reader.errors().add(new ImportRowError("status", "STATUS_INVALID", "El estado debe ser ACTIVE o INACTIVE"));
        }
    }

    private static void requirePositive(ImportRowReader reader, String column) {
        BigDecimal value = reader.decimal(column, false);
        if (reader.text(column).isEmpty() || value != null && value.signum() <= 0) {
            reader.errors().removeIf(error -> error.column().equals(column));
            reader.errors().add(new ImportRowError(column, "PRICE_NOT_POSITIVE", "El precio debe ser mayor que 0"));
        }
    }
}
