package com.red_escuela.backend.dataimport.importer;

import com.red_escuela.backend.academicstructure.api.HistoricalCycle;
import com.red_escuela.backend.academicstructure.api.HistoricalCycleRecorder;
import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import com.red_escuela.backend.dataimport.specialist.ImportEntityDefinition;
import com.red_escuela.backend.dataimport.specialist.ImportReference;
import com.red_escuela.backend.dataimport.specialist.ImportRowReader;
import com.red_escuela.backend.dataimport.specialist.ImportValueParser;
import com.red_escuela.backend.shared.time.ApplicationTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TermImportDefinition implements ImportEntityDefinition {
    public static final List<String> COLUMNS = List.of("year", "termType", "description", "startDate", "endDate",
            "programName");

    private final HistoricalCycleRecorder recorder;

    public TermImportDefinition(HistoricalCycleRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public ImportEntityType type() {
        return ImportEntityType.TERMS;
    }

    @Override
    public List<String> columns() {
        return COLUMNS;
    }

    public static String keyOf(String year, String termType) {
        return ImportValueParser.cleanText(year) + "|" + ImportValueParser.cleanText(termType).toUpperCase(Locale.ROOT);
    }

    @Override
    public String naturalKey(Map<String, String> payload) {
        return keyOf(payload.get("year"), payload.get("termType"));
    }

    @Override
    public Map<String, String> uniquenessValues(Map<String, String> payload) {
        return Map.of("term", naturalKey(payload));
    }

    @Override
    public List<ImportRowError> validateFormat(Map<String, String> payload) {
        ImportRowReader reader = new ImportRowReader(payload);
        reader.integer("year", true, 1990, 2100);
        reader.enumValue("termType", TermType.class, true);
        reader.requiredText("programName", 255);
        LocalDate start = reader.date("startDate", true);
        LocalDate end = reader.date("endDate", true);
        if (start != null && end != null && end.isBefore(start)) {
            reader.errors().add(new ImportRowError("endDate", "END_DATE_BEFORE_START_DATE", "El fin es anterior al inicio"));
        }
        if (end != null && end.isAfter(ApplicationTime.today())) {
            reader.errors().add(new ImportRowError("endDate", "END_DATE_IN_FUTURE", "Solo se importan ciclos ya cerrados"));
        }
        return reader.errors();
    }

    @Override
    public List<ImportReference> references(Map<String, String> payload) {
        return List.of(new ImportReference(ImportEntityType.PROGRAMS, ImportValueParser.cleanText(payload.get("programName")),
                "programName", "PROGRAM_NOT_FOUND"));
    }

    @Override
    public BaseCheck checkAgainstBase(Map<String, String> payload) {
        boolean exists = existsInBase(naturalKey(payload));
        return new BaseCheck(exists, exists
                ? List.of(new ImportRowError("year", "TERM_ALREADY_EXISTS", "Ya existe un ciclo con ese año y tipo"))
                : List.of());
    }

    @Override
    public boolean existsInBase(String naturalKey) {
        String[] parts = naturalKey.split("\\|", 2);
        try {
            return parts.length == 2 && recorder.cycleExists(Integer.parseInt(parts[0]), TermType.valueOf(parts[1]));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public Long record(Map<String, String> payload, Long institutionId) {
        ImportRowReader reader = new ImportRowReader(payload);
        return recorder.recordCycle(new HistoricalCycle(reader.text("programName"), reader.integer("year", true, 1990, 2100),
                reader.enumValue("termType", TermType.class, true).orElseThrow(), reader.text("description"),
                reader.date("startDate", true), reader.date("endDate", true)));
    }
}
