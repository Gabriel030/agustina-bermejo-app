package com.red_escuela.backend.dataimport.importer;

import com.red_escuela.backend.academicstructure.api.HistoricalSectionRecorder;
import com.red_escuela.backend.attendance.api.HistoricalAttendance;
import com.red_escuela.backend.attendance.api.HistoricalAttendanceRecorder;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import com.red_escuela.backend.dataimport.specialist.ImportDocumentKey;
import com.red_escuela.backend.dataimport.specialist.ImportEntityDefinition;
import com.red_escuela.backend.dataimport.specialist.ImportReference;
import com.red_escuela.backend.dataimport.specialist.ImportRowReader;
import com.red_escuela.backend.dataimport.specialist.ImportValueParser;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AttendanceImportDefinition implements ImportEntityDefinition {
    public static final List<String> COLUMNS = List.of("documentType", "dni", "sectionCode", "classDate", "status");
    private static final Set<String> ACCEPTED = Set.of("PRESENT", "ABSENT", "LATE", "JUSTIFIED");

    private final HistoricalAttendanceRecorder attendanceRecorder;
    private final HistoricalSectionRecorder sectionRecorder;

    public AttendanceImportDefinition(HistoricalAttendanceRecorder attendanceRecorder, HistoricalSectionRecorder sectionRecorder) {
        this.attendanceRecorder = attendanceRecorder;
        this.sectionRecorder = sectionRecorder;
    }

    @Override
    public ImportEntityType type() {
        return ImportEntityType.ATTENDANCES;
    }

    @Override
    public List<String> columns() {
        return COLUMNS;
    }

    @Override
    public String naturalKey(Map<String, String> payload) {
        LocalDate date = ImportValueParser.parseDate(ImportValueParser.cleanText(payload.get("classDate"))).orElse(null);
        return ImportDocumentKey.keyOf(payload) + "|" + ImportValueParser.cleanText(payload.get("sectionCode")) + "|"
                + (date == null ? ImportValueParser.cleanText(payload.get("classDate")) : date.toString());
    }

    @Override
    public Map<String, String> uniquenessValues(Map<String, String> payload) {
        return Map.of("attendanceDay", naturalKey(payload));
    }

    @Override
    public List<ImportRowError> validateFormat(Map<String, String> payload) {
        ImportRowReader reader = new ImportRowReader(payload);
        if (ImportDocumentKey.typeOf(payload).isEmpty()) {
            reader.errors().add(new ImportRowError("documentType", "DOCUMENT_TYPE_INVALID", "El tipo de documento debe ser DNI, PASSPORT u OTHER"));
        }
        reader.requiredText("dni", 20);
        reader.requiredText("sectionCode", 60);
        LocalDate classDate = reader.date("classDate", true);
        if (classDate != null && classDate.isAfter(ApplicationTime.today())) {
            reader.errors().add(new ImportRowError("classDate", "CLASS_DATE_IN_FUTURE", "La fecha de la clase es futura"));
        }
        String status = reader.requiredText("status", 30).toUpperCase(Locale.ROOT);
        if (!status.isEmpty() && !ACCEPTED.contains(status)) {
            reader.errors().add(new ImportRowError("status", "STATUS_NOT_ACCEPTED", "El estado debe ser PRESENT, ABSENT, LATE o JUSTIFIED"));
        }
        return reader.errors();
    }

    @Override
    public List<ImportReference> references(Map<String, String> payload) {
        return List.of(
                ImportReference.byAlias(ImportEntityType.SECTION_ENROLLMENTS,
                        SectionEnrollmentImportDefinition.studentSectionKey(ImportDocumentKey.keyOf(payload), payload.get("sectionCode")),
                        "sectionCode", "SECTION_ENROLLMENT_NOT_FOUND"),
                ImportReference.byAlias(ImportEntityType.SECTIONS,
                        SectionImportDefinition.leadInstructorAlias(ImportValueParser.cleanText(payload.get("sectionCode"))),
                        "sectionCode", "SECTION_WITHOUT_INSTRUCTOR"));
    }

    @Override
    public BaseCheck checkAgainstBase(Map<String, String> payload) {
        ImportRowReader reader = new ImportRowReader(payload);
        DocumentType type = ImportDocumentKey.typeOf(payload).orElseThrow();
        LocalDate classDate = reader.date("classDate", true);
        boolean exists = sectionRecorder.findSectionIdByCode(reader.text("sectionCode"))
                .map(sectionId -> attendanceRecorder.attendanceExists(type, ImportDocumentKey.numberOf(payload, type), sectionId, classDate))
                .orElse(false);
        List<ImportRowError> errors = new ArrayList<>();
        if (exists) {
            errors.add(new ImportRowError("classDate", "ATTENDANCE_ALREADY_EXISTS", "El alumno ya tiene asistencia cargada ese día"));
        }
        return new BaseCheck(exists, errors);
    }

    @Override
    public void discard(Long entityId) {
        attendanceRecorder.discardAttendance(entityId);
    }

    @Override
    public Long record(Map<String, String> payload, Long institutionId) {
        ImportRowReader reader = new ImportRowReader(payload);
        DocumentType type = ImportDocumentKey.typeOf(payload).orElseThrow();
        Long sectionId = sectionRecorder.findSectionIdByCode(reader.text("sectionCode")).orElseThrow();
        return attendanceRecorder.recordAttendance(new HistoricalAttendance(type, ImportDocumentKey.numberOf(payload, type),
                sectionId, reader.date("classDate", true), reader.text("status").toUpperCase(Locale.ROOT)));
    }
}
