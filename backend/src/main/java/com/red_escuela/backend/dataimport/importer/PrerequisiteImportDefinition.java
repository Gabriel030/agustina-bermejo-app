package com.red_escuela.backend.dataimport.importer;

import com.red_escuela.backend.academicstructure.api.HistoricalPrerequisite;
import com.red_escuela.backend.academicstructure.api.HistoricalProgramRecorder;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import com.red_escuela.backend.dataimport.specialist.ImportEntityDefinition;
import com.red_escuela.backend.dataimport.specialist.ImportReference;
import com.red_escuela.backend.dataimport.specialist.ImportRowReader;
import com.red_escuela.backend.dataimport.specialist.ImportValueParser;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PrerequisiteImportDefinition implements ImportEntityDefinition {
    public static final List<String> COLUMNS = List.of("programName", "subjectCode", "prerequisiteCode",
            "requirementType", "minGrade");

    private final HistoricalProgramRecorder recorder;

    public PrerequisiteImportDefinition(HistoricalProgramRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public ImportEntityType type() {
        return ImportEntityType.PREREQUISITES;
    }

    @Override
    public List<String> columns() {
        return COLUMNS;
    }

    @Override
    public String naturalKey(Map<String, String> payload) {
        return clean(payload, "programName") + "|" + clean(payload, "subjectCode") + "|" + clean(payload, "prerequisiteCode");
    }

    @Override
    public Map<String, String> uniquenessValues(Map<String, String> payload) {
        return Map.of("prerequisite", naturalKey(payload));
    }

    @Override
    public List<ImportRowError> validateFormat(Map<String, String> payload) {
        ImportRowReader reader = new ImportRowReader(payload);
        reader.requiredText("programName", 255);
        reader.requiredText("subjectCode", 4);
        reader.requiredText("prerequisiteCode", 4);
        reader.enumValue("requirementType", RequirementType.class, true);
        BigDecimal minGrade = reader.decimal("minGrade", false);
        if (minGrade != null && (minGrade.signum() < 0 || minGrade.compareTo(BigDecimal.TEN) > 0)) {
            reader.errors().add(new ImportRowError("minGrade", "MIN_GRADE_OUT_OF_RANGE", "La nota mínima debe estar entre 0 y 10"));
        }
        if (!clean(payload, "subjectCode").isEmpty() && clean(payload, "subjectCode").equals(clean(payload, "prerequisiteCode"))) {
            reader.errors().add(new ImportRowError("prerequisiteCode", "PREREQUISITE_CYCLE", "Una materia no puede ser correlativa de sí misma"));
        }
        return reader.errors();
    }

    @Override
    public List<ImportReference> references(Map<String, String> payload) {
        String program = clean(payload, "programName");
        return List.of(
                new ImportReference(ImportEntityType.CURRICULUM, CurriculumImportDefinition.keyOf(program, payload.get("subjectCode")), "subjectCode", "PLAN_ENTRY_NOT_FOUND"),
                new ImportReference(ImportEntityType.CURRICULUM, CurriculumImportDefinition.keyOf(program, payload.get("prerequisiteCode")), "prerequisiteCode", "PLAN_ENTRY_NOT_FOUND"));
    }

    @Override
    public boolean validatesAcrossRows() {
        return true;
    }

    @Override
    public Map<Integer, List<ImportRowError>> validateAcrossRows(List<Map<String, String>> payloads) {
        Map<String, Map<String, Set<String>>> graphByProgram = new HashMap<>();
        for (Map<String, String> payload : payloads) {
            graphByProgram.computeIfAbsent(clean(payload, "programName"), key -> new HashMap<>())
                    .computeIfAbsent(clean(payload, "subjectCode"), key -> new HashSet<>()).add(clean(payload, "prerequisiteCode"));
        }
        Map<Integer, List<ImportRowError>> errors = new HashMap<>();
        for (int index = 0; index < payloads.size(); index++) {
            Map<String, String> payload = payloads.get(index);
            Map<String, Set<String>> graph = graphByProgram.get(clean(payload, "programName"));
            String subject = clean(payload, "subjectCode");
            String prerequisite = clean(payload, "prerequisiteCode");
            if (!subject.equals(prerequisite) && reaches(graph, prerequisite, subject)) {
                errors.computeIfAbsent(index, key -> new ArrayList<>()).add(new ImportRowError("prerequisiteCode",
                        "PREREQUISITE_CYCLE", "Las correlativas forman un ciclo"));
            }
        }
        return errors;
    }

    @Override
    public BaseCheck checkAgainstBase(Map<String, String> payload) {
        boolean exists = recorder.prerequisiteExists(clean(payload, "programName"), clean(payload, "subjectCode"),
                clean(payload, "prerequisiteCode"));
        return new BaseCheck(exists, exists
                ? List.of(new ImportRowError("prerequisiteCode", "PREREQUISITE_ALREADY_EXISTS", "La correlativa ya existe"))
                : List.of());
    }

    @Override
    public Long record(Map<String, String> payload, Long institutionId) {
        ImportRowReader reader = new ImportRowReader(payload);
        return recorder.recordPrerequisite(new HistoricalPrerequisite(reader.text("programName"), reader.text("subjectCode"),
                reader.text("prerequisiteCode"), reader.enumValue("requirementType", RequirementType.class, true).orElseThrow(),
                reader.decimal("minGrade", false)));
    }

    private static boolean reaches(Map<String, Set<String>> graph, String from, String target) {
        Deque<String> pending = new ArrayDeque<>(List.of(from));
        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            String current = pending.pop();
            if (current.equals(target)) {
                return true;
            }
            if (visited.add(current)) {
                pending.addAll(graph.getOrDefault(current, Set.of()));
            }
        }
        return false;
    }

    private static String clean(Map<String, String> payload, String column) {
        return ImportValueParser.cleanText(payload.get(column));
    }
}
