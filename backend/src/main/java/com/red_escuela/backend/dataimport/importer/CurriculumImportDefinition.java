package com.red_escuela.backend.dataimport.importer;

import com.red_escuela.backend.academicstructure.api.HistoricalPlanEntry;
import com.red_escuela.backend.academicstructure.api.HistoricalProgramRecorder;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.academicstructure.entity.SubjectRecurrence;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import com.red_escuela.backend.dataimport.specialist.ImportEntityDefinition;
import com.red_escuela.backend.dataimport.specialist.ImportReference;
import com.red_escuela.backend.dataimport.specialist.ImportRowReader;
import com.red_escuela.backend.dataimport.specialist.ImportValueParser;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CurriculumImportDefinition implements ImportEntityDefinition {
    public static final List<String> COLUMNS = List.of("programName", "subjectCode", "curricularYear",
            "curricularTerm", "credits", "category", "recurrence", "sortOrder");

    private final HistoricalProgramRecorder recorder;

    public CurriculumImportDefinition(HistoricalProgramRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public ImportEntityType type() {
        return ImportEntityType.CURRICULUM;
    }

    @Override
    public List<String> columns() {
        return COLUMNS;
    }

    public static String keyOf(String programName, String subjectCode) {
        return ImportValueParser.cleanText(programName) + "|" + ImportValueParser.cleanText(subjectCode);
    }

    @Override
    public String naturalKey(Map<String, String> payload) {
        return keyOf(payload.get("programName"), payload.get("subjectCode"));
    }

    @Override
    public Map<String, String> uniquenessValues(Map<String, String> payload) {
        return Map.of("programSubject", naturalKey(payload));
    }

    @Override
    public List<ImportRowError> validateFormat(Map<String, String> payload) {
        ImportRowReader reader = new ImportRowReader(payload);
        reader.requiredText("programName", 255);
        reader.requiredText("subjectCode", 4);
        reader.integer("curricularYear", false, 1, 15);
        reader.enumValue("curricularTerm", CurricularTerm.class, false);
        reader.integer("credits", true, 0, 1000);
        reader.enumValue("category", ProgramSubjectCategory.class, false);
        reader.enumValue("recurrence", SubjectRecurrence.class, false);
        reader.integer("sortOrder", false, 0, 10000);
        return reader.errors();
    }

    @Override
    public List<ImportReference> references(Map<String, String> payload) {
        return List.of(
                new ImportReference(ImportEntityType.PROGRAMS, ImportValueParser.cleanText(payload.get("programName")), "programName", "PROGRAM_NOT_FOUND"),
                new ImportReference(ImportEntityType.SUBJECTS, ImportValueParser.cleanText(payload.get("subjectCode")), "subjectCode", "SUBJECT_NOT_FOUND"));
    }

    @Override
    public BaseCheck checkAgainstBase(Map<String, String> payload) {
        boolean exists = recorder.planEntryExists(ImportValueParser.cleanText(payload.get("programName")),
                ImportValueParser.cleanText(payload.get("subjectCode")));
        return new BaseCheck(exists, exists
                ? List.of(new ImportRowError("subjectCode", "PLAN_ENTRY_ALREADY_EXISTS", "La materia ya está en el plan de la carrera"))
                : List.of());
    }

    @Override
    public boolean existsInBase(String naturalKey) {
        String[] parts = naturalKey.split("\\|", 2);
        return parts.length == 2 && recorder.planEntryExists(parts[0], parts[1]);
    }

    @Override
    public Long record(Map<String, String> payload, Long institutionId) {
        ImportRowReader reader = new ImportRowReader(payload);
        return recorder.recordPlanEntry(new HistoricalPlanEntry(reader.text("programName"), reader.text("subjectCode"),
                reader.integer("curricularYear", false, 1, 15),
                reader.enumValue("curricularTerm", CurricularTerm.class, false).orElse(null),
                reader.integer("credits", true, 0, 1000),
                reader.enumValue("category", ProgramSubjectCategory.class, false).orElse(ProgramSubjectCategory.MANDATORY),
                reader.enumValue("recurrence", SubjectRecurrence.class, false).orElse(null),
                reader.integer("sortOrder", false, 0, 10000)));
    }
}
