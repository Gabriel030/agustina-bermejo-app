package com.red_escuela.backend.dataimport.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.red_escuela.backend.academicstructure.api.HistoricalProgramRecorder;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StructureImportDefinitionsTest {
    private final HistoricalProgramRecorder recorder = mock(HistoricalProgramRecorder.class);
    private final ProgramImportDefinition programs = new ProgramImportDefinition(recorder);
    private final SubjectImportDefinition subjects = new SubjectImportDefinition(recorder);
    private final PrerequisiteImportDefinition prerequisites = new PrerequisiteImportDefinition(recorder);

    private static Map<String, String> program(String price, String fee) {
        return Map.ofEntries(Map.entry("programName", "Tecnicatura"), Map.entry("level", "UNDERGRADUATE"),
                Map.entry("totalYears", "3"), Map.entry("totalCreditsRequired", "120"), Map.entry("totalHours", "1800"),
                Map.entry("teachingMode", "IN_PERSON"), Map.entry("status", "ACTIVE"), Map.entry("listPrice", price),
                Map.entry("listEnrollmentFee", fee), Map.entry("currency", "ARS"), Map.entry("graduationProfile", ""));
    }

    private static Map<String, String> prerequisite(String subject, String prerequisite) {
        return Map.of("programName", "Tecnicatura", "subjectCode", subject, "prerequisiteCode", prerequisite,
                "requirementType", "APPROVED", "minGrade", "4");
    }

    private static List<String> codes(List<ImportRowError> errors) {
        return errors.stream().map(ImportRowError::code).toList();
    }

    @Test
    void validProgramHasNoFormatErrors() {
        assertThat(programs.validateFormat(program("85000", "40000"))).isEmpty();
    }

    @Test
    void programPriceMustBePositive() {
        assertThat(codes(programs.validateFormat(program("0", "40000")))).containsExactly("PRICE_NOT_POSITIVE");
        assertThat(codes(programs.validateFormat(program("85000", "")))).containsExactly("PRICE_NOT_POSITIVE");
    }

    @Test
    void programWithZeroRequiredCreditsIsRejected() {
        Map<String, String> payload = new java.util.HashMap<>(program("85000", "40000"));
        payload.put("totalCreditsRequired", "0");
        assertThat(codes(programs.validateFormat(payload))).containsExactly("TOTAL_CREDITS_REQUIRED_OUT_OF_RANGE");
    }

    @Test
    void subjectCodeLongerThanFourCharactersIsRejected() {
        Map<String, String> payload = Map.of("subjectCode", "ADMIN", "name", "Administración", "weeklyHours", "4",
                "gradeMode", "NUMERIC", "maxFinalExamAttempts", "3", "status", "ACTIVE");
        assertThat(codes(subjects.validateFormat(payload))).containsExactly("SUBJECT_CODE_TOO_LONG");
    }

    @Test
    void everyRowOnAPrerequisiteCycleIsFlaggedAndOthersAreNot() {
        Map<Integer, List<ImportRowError>> errors = prerequisites.validateAcrossRows(List.of(
                prerequisite("B", "A"), prerequisite("C", "B"), prerequisite("A", "C"), prerequisite("D", "A")));

        assertThat(errors.keySet()).containsExactlyInAnyOrder(0, 1, 2);
        assertThat(codes(errors.get(0))).containsExactly("PREREQUISITE_CYCLE");
    }

    @Test
    void acyclicPrerequisitesProduceNoCycleErrors() {
        assertThat(prerequisites.validateAcrossRows(List.of(prerequisite("B", "A"), prerequisite("C", "B"),
                prerequisite("C", "A")))).isEmpty();
    }

    @Test
    void sameSubjectCodeInDifferentProgramsIsNotACycle() {
        Map<String, String> other = new java.util.HashMap<>(prerequisite("A", "B"));
        other.put("programName", "Otra");
        assertThat(prerequisites.validateAcrossRows(List.of(prerequisite("B", "A"), other))).isEmpty();
    }

    @Test
    void selfPrerequisiteIsRejectedAtFormatLevel() {
        assertThat(codes(prerequisites.validateFormat(prerequisite("A", "A")))).containsExactly("PREREQUISITE_CYCLE");
    }
}
