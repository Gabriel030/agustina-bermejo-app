package com.red_escuela.backend.dataimport.importer;

import com.red_escuela.backend.billing.api.HistoricalCohortIntake;
import com.red_escuela.backend.billing.api.HistoricalProgramCohort;
import com.red_escuela.backend.billing.api.HistoricalProgramCohortRecorder;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import com.red_escuela.backend.dataimport.specialist.ImportDocumentKey;
import com.red_escuela.backend.dataimport.specialist.ImportEntityDefinition;
import com.red_escuela.backend.dataimport.specialist.ImportReference;
import com.red_escuela.backend.dataimport.specialist.ImportRowReader;
import com.red_escuela.backend.dataimport.specialist.ImportValueParser;
import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.enrollment.api.HistoricalEnrollmentRecorder;
import com.red_escuela.backend.enrollment.api.HistoricalProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProgramEnrollmentImportDefinition implements ImportEntityDefinition {
    public static final List<String> COLUMNS = List.of("documentType", "dni", "programName", "enrollmentDate", "status",
            "completionDate", "withdrawalDate", "cohortYear", "intake");

    private final HistoricalEnrollmentRecorder recorder;
    private final HistoricalProgramCohortRecorder cohortRecorder;

    public ProgramEnrollmentImportDefinition(HistoricalEnrollmentRecorder recorder, HistoricalProgramCohortRecorder cohortRecorder) {
        this.recorder = recorder;
        this.cohortRecorder = cohortRecorder;
    }

    @Override
    public ImportEntityType type() {
        return ImportEntityType.PROGRAM_ENROLLMENTS;
    }

    @Override
    public List<String> columns() {
        return COLUMNS;
    }

    public static String keyOf(String personKey, String programName) {
        return personKey + "|" + ImportValueParser.cleanText(programName);
    }

    @Override
    public String naturalKey(Map<String, String> payload) {
        return keyOf(ImportDocumentKey.keyOf(payload), payload.get("programName"));
    }

    @Override
    public Map<String, String> uniquenessValues(Map<String, String> payload) {
        return Map.of("studentProgram", naturalKey(payload));
    }

    @Override
    public List<ImportRowError> validateFormat(Map<String, String> payload) {
        ImportRowReader reader = new ImportRowReader(payload);
        if (ImportDocumentKey.typeOf(payload).isEmpty()) {
            reader.errors().add(new ImportRowError("documentType", "DOCUMENT_TYPE_INVALID", "El tipo de documento debe ser DNI, PASSPORT u OTHER"));
        }
        reader.requiredText("dni", 20);
        reader.requiredText("programName", 255);
        LocalDate enrollmentDate = reader.date("enrollmentDate", true);
        ProgramEnrollmentStatus status = reader.enumValue("status", ProgramEnrollmentStatus.class, true).orElse(null);
        LocalDate completionDate = reader.date("completionDate", false);
        LocalDate withdrawalDate = reader.date("withdrawalDate", false);
        Integer cohortYear = reader.integer("cohortYear", false, 1990, 2100);
        Optional<HistoricalCohortIntake> intake = reader.enumValue("intake", HistoricalCohortIntake.class, cohortYear != null);
        LocalDate today = ApplicationTime.today();
        if (enrollmentDate != null && enrollmentDate.isAfter(today)) {
            reader.errors().add(new ImportRowError("enrollmentDate", "ENROLLMENT_DATE_IN_FUTURE", "La fecha de inscripción es futura"));
        }
        if ((status == ProgramEnrollmentStatus.COMPLETED || status == ProgramEnrollmentStatus.WITHDRAWN) && completionDate == null) {
            reader.errors().add(new ImportRowError("completionDate", "COMPLETION_DATE_REQUIRED", "Falta la fecha de egreso o de baja"));
        }
        if (completionDate != null && (completionDate.isAfter(today)
                || enrollmentDate != null && completionDate.isBefore(enrollmentDate))) {
            reader.errors().add(new ImportRowError("completionDate", "COMPLETION_DATE_INVALID", "La fecha de egreso o baja es futura o anterior a la inscripción"));
        }
        if (withdrawalDate != null && status != ProgramEnrollmentStatus.WITHDRAWN) {
            reader.errors().add(new ImportRowError("withdrawalDate", "WITHDRAWAL_DATE_ONLY_WHEN_WITHDRAWN", "La fecha de baja solo aplica al estado WITHDRAWN"));
        }
        if (cohortYear != null && cohortYear > today.getYear()) {
            reader.errors().add(new ImportRowError("cohortYear", "COHORT_YEAR_IN_FUTURE", "El año de ingreso es posterior al año actual"));
        }
        if (cohortYear == null && intake.isPresent()) {
            reader.errors().add(new ImportRowError("cohortYear", "COHORT_YEAR_REQUIRED_WITH_INTAKE", "Falta el año de ingreso de la cohorte"));
        }
        return reader.errors();
    }

    @Override
    public List<ImportReference> references(Map<String, String> payload) {
        List<ImportReference> references = new ArrayList<>(List.of(
                new ImportReference(ImportEntityType.PERSONS, ImportDocumentKey.keyOf(payload), "dni", "STUDENT_NOT_FOUND"),
                new ImportReference(ImportEntityType.PROGRAMS, ImportValueParser.cleanText(payload.get("programName")),
                        "programName", "PROGRAM_NOT_FOUND")));
        String cohortYear = ImportValueParser.cleanText(payload.get("cohortYear"));
        if (!cohortYear.isEmpty()) {
            references.add(new ImportReference(ImportEntityType.TERMS,
                    TermImportDefinition.keyOf(cohortYear, TermType.ANNUAL.name(), payload.get("programName")),
                    "cohortYear", "COHORT_ANNUAL_CYCLE_NOT_FOUND"));
        }
        return references;
    }

    @Override
    public BaseCheck checkAgainstBase(Map<String, String> payload) {
        boolean exists = existsInBase(naturalKey(payload));
        return new BaseCheck(exists, exists
                ? List.of(new ImportRowError("programName", "PROGRAM_ENROLLMENT_ALREADY_EXISTS", "El alumno ya está inscripto en la carrera"))
                : List.of());
    }

    @Override
    public boolean existsInBase(String naturalKey) {
        String[] parts = naturalKey.split("\\|", 2);
        if (parts.length != 2) {
            return false;
        }
        return recorder.programEnrollmentExists(ImportDocumentKey.typeOfKey(parts[0]), ImportDocumentKey.numberOfKey(parts[0]), parts[1]);
    }

    @Override
    public void discard(Long entityId) {
        recorder.discardProgramEnrollment(entityId);
    }

    @Override
    public Long record(Map<String, String> payload, Long institutionId) {
        ImportRowReader reader = new ImportRowReader(payload);
        DocumentType type = ImportDocumentKey.typeOf(payload).orElseThrow();
        Integer cohortYear = reader.integer("cohortYear", false, 1990, 2100);
        Long enrollmentId = recorder.recordProgramEnrollment(new HistoricalProgramEnrollment(type,
                ImportDocumentKey.numberOf(payload, type), reader.text("programName"), reader.date("enrollmentDate", true),
                reader.enumValue("status", ProgramEnrollmentStatus.class, true).orElseThrow(),
                reader.date("completionDate", false), reader.date("withdrawalDate", false), cohortYear));
        if (cohortYear != null) {
            cohortRecorder.assignCohort(enrollmentId, new HistoricalProgramCohort(reader.text("programName"), cohortYear,
                    reader.enumValue("intake", HistoricalCohortIntake.class, true).orElseThrow()));
        }
        return enrollmentId;
    }
}
