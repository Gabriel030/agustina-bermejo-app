package com.red_escuela.backend.dataimport.importer;

import com.red_escuela.backend.academicstructure.api.HistoricalProgramRecorder;
import com.red_escuela.backend.academicstructure.api.HistoricalSubject;
import com.red_escuela.backend.academicstructure.entity.GradeMode;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import com.red_escuela.backend.dataimport.specialist.ImportEntityDefinition;
import com.red_escuela.backend.dataimport.specialist.ImportRowReader;
import com.red_escuela.backend.dataimport.specialist.ImportValueParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SubjectImportDefinition implements ImportEntityDefinition {
    public static final List<String> COLUMNS = List.of("subjectCode", "name", "weeklyHours", "gradeMode",
            "maxFinalExamAttempts", "status");

    private final HistoricalProgramRecorder recorder;

    public SubjectImportDefinition(HistoricalProgramRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public ImportEntityType type() {
        return ImportEntityType.SUBJECTS;
    }

    @Override
    public List<String> columns() {
        return COLUMNS;
    }

    @Override
    public String naturalKey(Map<String, String> payload) {
        return ImportValueParser.cleanText(payload.get("subjectCode"));
    }

    @Override
    public Map<String, String> uniquenessValues(Map<String, String> payload) {
        return Map.of("subjectCode", naturalKey(payload), "name", ImportValueParser.cleanText(payload.get("name")));
    }

    @Override
    public List<ImportRowError> validateFormat(Map<String, String> payload) {
        ImportRowReader reader = new ImportRowReader(payload);
        reader.requiredText("subjectCode", 4);
        reader.requiredText("name", 255);
        reader.integer("weeklyHours", false, 1, 100);
        reader.enumValue("gradeMode", GradeMode.class, false);
        reader.integer("maxFinalExamAttempts", false, 1, 20);
        String status = reader.text("status");
        if (!status.equalsIgnoreCase("ACTIVE") && !status.equalsIgnoreCase("INACTIVE")) {
            reader.errors().add(new ImportRowError("status", "STATUS_INVALID", "El estado debe ser ACTIVE o INACTIVE"));
        }
        return reader.errors();
    }

    @Override
    public BaseCheck checkAgainstBase(Map<String, String> payload) {
        List<ImportRowError> errors = new ArrayList<>();
        boolean codeExists = recorder.subjectCodeExists(naturalKey(payload));
        if (codeExists) {
            errors.add(new ImportRowError("subjectCode", "SUBJECT_ALREADY_EXISTS", "Ya existe una materia con ese código"));
        } else if (recorder.subjectNameExists(ImportValueParser.cleanText(payload.get("name")))) {
            errors.add(new ImportRowError("name", "SUBJECT_NAME_ALREADY_EXISTS", "Ya existe una materia con ese nombre"));
        }
        return new BaseCheck(codeExists, errors);
    }

    @Override
    public boolean existsInBase(String naturalKey) {
        return recorder.subjectCodeExists(naturalKey);
    }

    @Override
    public Long record(Map<String, String> payload, Long institutionId) {
        ImportRowReader reader = new ImportRowReader(payload);
        return recorder.recordSubject(new HistoricalSubject(reader.text("subjectCode"), reader.text("name"),
                reader.integer("weeklyHours", false, 1, 100), reader.enumValue("gradeMode", GradeMode.class, false).orElse(null),
                reader.integer("maxFinalExamAttempts", false, 1, 20), !"INACTIVE".equalsIgnoreCase(reader.text("status"))));
    }
}
