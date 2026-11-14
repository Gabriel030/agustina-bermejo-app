package com.red_escuela.backend.dataimport.importer;

import com.red_escuela.backend.academicstructure.api.HistoricalSectionRecorder;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import com.red_escuela.backend.dataimport.specialist.ImportDocumentKey;
import com.red_escuela.backend.dataimport.specialist.ImportEntityDefinition;
import com.red_escuela.backend.dataimport.specialist.ImportReference;
import com.red_escuela.backend.dataimport.specialist.ImportRowReader;
import com.red_escuela.backend.dataimport.specialist.ImportValueParser;
import com.red_escuela.backend.enrollment.api.HistoricalEnrollmentRecorder;
import com.red_escuela.backend.enrollment.api.HistoricalSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.evaluation.api.HistoricalClosureRecorder;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SectionEnrollmentImportDefinition implements ImportEntityDefinition {
    public static final List<String> COLUMNS = List.of("documentType", "dni", "programName", "sectionCode",
            "attemptNumber", "status", "finalScore", "finalGradeType", "finalDate");
    private static final Set<EnrollmentStatus> ACCEPTED = EnumSet.of(EnrollmentStatus.APPROVED, EnrollmentStatus.REGULAR,
            EnrollmentStatus.FAILED, EnrollmentStatus.WITHDRAWN);
    private static final Set<EnrollmentStatus> GRADED = EnumSet.of(EnrollmentStatus.APPROVED, EnrollmentStatus.FAILED);

    private final HistoricalEnrollmentRecorder enrollmentRecorder;
    private final HistoricalSectionRecorder sectionRecorder;
    private final HistoricalClosureRecorder closureRecorder;

    public SectionEnrollmentImportDefinition(HistoricalEnrollmentRecorder enrollmentRecorder,
            HistoricalSectionRecorder sectionRecorder, HistoricalClosureRecorder closureRecorder) {
        this.enrollmentRecorder = enrollmentRecorder;
        this.sectionRecorder = sectionRecorder;
        this.closureRecorder = closureRecorder;
    }

    @Override
    public ImportEntityType type() {
        return ImportEntityType.SECTION_ENROLLMENTS;
    }

    @Override
    public List<String> columns() {
        return COLUMNS;
    }

    @Override
    public String naturalKey(Map<String, String> payload) {
        return ImportDocumentKey.keyOf(payload) + "|" + ImportValueParser.cleanText(payload.get("programName")) + "|"
                + ImportValueParser.cleanText(payload.get("sectionCode")) + "|" + ImportValueParser.cleanText(payload.get("attemptNumber"));
    }

    @Override
    public Map<String, String> uniquenessValues(Map<String, String> payload) {
        return Map.of("studentSection", ImportDocumentKey.keyOf(payload) + "|" + ImportValueParser.cleanText(payload.get("sectionCode")));
    }

    @Override
    public List<ImportRowError> validateFormat(Map<String, String> payload) {
        ImportRowReader reader = new ImportRowReader(payload);
        if (ImportDocumentKey.typeOf(payload).isEmpty()) {
            reader.errors().add(new ImportRowError("documentType", "DOCUMENT_TYPE_INVALID", "El tipo de documento debe ser DNI, PASSPORT u OTHER"));
        }
        reader.requiredText("dni", 20);
        reader.requiredText("programName", 255);
        reader.requiredText("sectionCode", 60);
        reader.integer("attemptNumber", true, 1, 20);
        EnrollmentStatus status = reader.enumValue("status", EnrollmentStatus.class, true).orElse(null);
        if (status != null && !ACCEPTED.contains(status)) {
            reader.errors().add(new ImportRowError("status", "STATUS_NOT_ACCEPTED", "El estado debe ser APPROVED, REGULAR, FAILED o WITHDRAWN"));
        }
        BigDecimal score = reader.decimal("finalScore", false);
        FinalGradeType gradeType = reader.enumValue("finalGradeType", FinalGradeType.class, false).orElse(null);
        LocalDate finalDate = reader.date("finalDate", false);
        if (score != null && (score.compareTo(BigDecimal.ONE) < 0 || score.compareTo(BigDecimal.TEN) > 0
                || score.stripTrailingZeros().scale() > 2)) {
            reader.errors().add(new ImportRowError("finalScore", "FINAL_SCORE_OUT_OF_RANGE", "La nota va de 1 a 10 con hasta 2 decimales"));
        }
        if (status != null && GRADED.contains(status)) {
            requireGradeData(reader, score, gradeType, finalDate);
        } else if (status != null && (score != null || gradeType != null || finalDate != null)) {
            reader.errors().add(new ImportRowError("finalScore", "FINAL_SCORE_NOT_ALLOWED", "Este estado no lleva nota ni fecha final"));
        }
        if (finalDate != null && finalDate.isAfter(ApplicationTime.today())) {
            reader.errors().add(new ImportRowError("finalDate", "FINAL_DATE_IN_FUTURE", "La fecha de la nota es futura"));
        }
        return reader.errors();
    }

    private static void requireGradeData(ImportRowReader reader, BigDecimal score, FinalGradeType gradeType, LocalDate finalDate) {
        if (score == null) {
            reader.errors().add(new ImportRowError("finalScore", "FINAL_SCORE_REQUIRED", "Falta la nota final"));
        }
        if (gradeType == null) {
            reader.errors().add(new ImportRowError("finalGradeType", "FINAL_GRADE_TYPE_REQUIRED", "Falta el tipo de nota final"));
        }
        if (finalDate == null) {
            reader.errors().add(new ImportRowError("finalDate", "FINAL_DATE_REQUIRED", "Falta la fecha de la nota"));
        }
    }

    @Override
    public List<ImportReference> references(Map<String, String> payload) {
        return List.of(
                new ImportReference(ImportEntityType.PROGRAM_ENROLLMENTS, ProgramEnrollmentImportDefinition.keyOf(
                        ImportDocumentKey.keyOf(payload), payload.get("programName")), "programName", "PROGRAM_ENROLLMENT_NOT_FOUND"),
                new ImportReference(ImportEntityType.SECTIONS, ImportValueParser.cleanText(payload.get("sectionCode")),
                        "sectionCode", "SECTION_NOT_FOUND"));
    }

    @Override
    public BaseCheck checkAgainstBase(Map<String, String> payload) {
        DocumentType type = ImportDocumentKey.typeOf(payload).orElseThrow();
        String number = ImportDocumentKey.numberOf(payload, type);
        List<ImportRowError> errors = new java.util.ArrayList<>();
        boolean exists = sectionRecorder.findSectionIdByCode(ImportValueParser.cleanText(payload.get("sectionCode")))
                .map(sectionId -> enrollmentRecorder.sectionEnrollmentExists(type, number, sectionId)).orElse(false);
        if (exists) {
            errors.add(new ImportRowError("sectionCode", "SECTION_ENROLLMENT_ALREADY_EXISTS", "El alumno ya cursó esa comisión"));
        }
        ImportRowReader reader = new ImportRowReader(payload);
        LocalDate finalDate = reader.date("finalDate", false);
        if (finalDate != null) {
            enrollmentRecorder.findProgramEnrollmentDate(type, number, reader.text("programName"))
                    .filter(finalDate::isBefore)
                    .ifPresent(date -> errors.add(new ImportRowError("finalDate", "DATE_BEFORE_ENROLLMENT",
                            "La fecha de la nota es anterior a la inscripción a la carrera")));
        }
        return new BaseCheck(exists, errors);
    }

    @Override
    public void discard(Long entityId) {
        closureRecorder.discardStudentResult(entityId);
        enrollmentRecorder.discardSectionEnrollment(entityId);
    }

    @Override
    public Long record(Map<String, String> payload, Long institutionId) {
        ImportRowReader reader = new ImportRowReader(payload);
        DocumentType type = ImportDocumentKey.typeOf(payload).orElseThrow();
        Long sectionId = sectionRecorder.findSectionIdByCode(reader.text("sectionCode")).orElseThrow();
        EnrollmentStatus status = reader.enumValue("status", EnrollmentStatus.class, true).orElseThrow();
        FinalGradeType gradeType = reader.enumValue("finalGradeType", FinalGradeType.class, false).orElse(null);
        BigDecimal score = reader.decimal("finalScore", false);
        Long enrollmentId = enrollmentRecorder.recordSectionEnrollment(new HistoricalSectionEnrollment(type,
                ImportDocumentKey.numberOf(payload, type), reader.text("programName"), sectionId,
                reader.integer("attemptNumber", true, 1, 20), status, score, gradeType, reader.date("finalDate", false)));
        closureRecorder.recordStudentResult(sectionId, enrollmentId, status, gradeType, score);
        return enrollmentId;
    }
}
