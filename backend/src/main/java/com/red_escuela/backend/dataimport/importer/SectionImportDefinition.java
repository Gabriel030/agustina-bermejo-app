package com.red_escuela.backend.dataimport.importer;

import com.red_escuela.backend.academicstructure.api.HistoricalSection;
import com.red_escuela.backend.academicstructure.api.HistoricalSectionRecorder;
import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import com.red_escuela.backend.dataimport.specialist.ImportEntityDefinition;
import com.red_escuela.backend.dataimport.specialist.ImportReference;
import com.red_escuela.backend.dataimport.specialist.ImportRowReader;
import com.red_escuela.backend.dataimport.specialist.ImportValueParser;
import com.red_escuela.backend.evaluation.api.HistoricalClosure;
import com.red_escuela.backend.evaluation.api.HistoricalClosureRecorder;
import com.red_escuela.backend.shared.time.ApplicationTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SectionImportDefinition implements ImportEntityDefinition {
    public static final List<String> COLUMNS = List.of("sectionCode", "subjectCode", "year", "termType", "name", "shift",
            "instructorDni", "closedOn", "actaNumber", "actaBook", "actaFolio");

    private final HistoricalSectionRecorder sectionRecorder;
    private final HistoricalClosureRecorder closureRecorder;

    public SectionImportDefinition(HistoricalSectionRecorder sectionRecorder, HistoricalClosureRecorder closureRecorder) {
        this.sectionRecorder = sectionRecorder;
        this.closureRecorder = closureRecorder;
    }

    @Override
    public ImportEntityType type() {
        return ImportEntityType.SECTIONS;
    }

    @Override
    public List<String> columns() {
        return COLUMNS;
    }

    @Override
    public String naturalKey(Map<String, String> payload) {
        return ImportValueParser.cleanText(payload.get("sectionCode"));
    }

    @Override
    public Map<String, String> uniquenessValues(Map<String, String> payload) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put("sectionCode", naturalKey(payload));
        String acta = ImportValueParser.cleanText(payload.get("actaNumber"));
        if (!acta.isEmpty()) {
            values.put("actaNumber", acta);
        }
        return values;
    }

    @Override
    public List<ImportRowError> validateFormat(Map<String, String> payload) {
        ImportRowReader reader = new ImportRowReader(payload);
        reader.requiredText("sectionCode", 60);
        reader.requiredText("subjectCode", 4);
        reader.integer("year", true, 1990, 2100);
        reader.enumValue("termType", TermType.class, true);
        reader.requiredText("name", 255);
        reader.enumValue("shift", SectionShift.class, false);
        LocalDate closedOn = reader.date("closedOn", true);
        if (closedOn != null && closedOn.isAfter(ApplicationTime.today())) {
            reader.errors().add(new ImportRowError("closedOn", "CLOSED_ON_IN_FUTURE", "Solo se importan comisiones ya cerradas"));
        }
        if (reader.text("actaNumber").length() > 20) {
            reader.errors().add(new ImportRowError("actaNumber", "ACTA_NUMBER_TOO_LONG", "El número de acta supera 20 caracteres"));
        }
        if (reader.text("actaBook").length() > 50 || reader.text("actaFolio").length() > 50) {
            reader.errors().add(new ImportRowError("actaBook", "ACTA_BOOK_OR_FOLIO_TOO_LONG", "Libro o folio superan 50 caracteres"));
        }
        return reader.errors();
    }

    @Override
    public List<ImportReference> references(Map<String, String> payload) {
        List<ImportReference> references = new ArrayList<>();
        references.add(new ImportReference(ImportEntityType.SUBJECTS, ImportValueParser.cleanText(payload.get("subjectCode")),
                "subjectCode", "SUBJECT_NOT_FOUND"));
        references.add(new ImportReference(ImportEntityType.TERMS,
                TermImportDefinition.keyOf(payload.get("year"), payload.get("termType")), "year", "TERM_NOT_FOUND"));
        String instructor = ImportValueParser.cleanDocumentNumber(payload.get("instructorDni"));
        if (!instructor.isEmpty()) {
            references.add(new ImportReference(ImportEntityType.PERSONS, "DNI:" + instructor, "instructorDni", "INSTRUCTOR_NOT_FOUND"));
        }
        return references;
    }

    @Override
    public BaseCheck checkAgainstBase(Map<String, String> payload) {
        boolean exists = sectionRecorder.sectionCodeExists(naturalKey(payload));
        List<ImportRowError> errors = new ArrayList<>();
        if (exists) {
            errors.add(new ImportRowError("sectionCode", "SECTION_ALREADY_EXISTS", "Ya existe una comisión con ese código"));
        }
        String acta = ImportValueParser.cleanText(payload.get("actaNumber"));
        if (!acta.isEmpty() && closureRecorder.reportNumberExists(acta)) {
            errors.add(new ImportRowError("actaNumber", "ACTA_NUMBER_ALREADY_EXISTS", "Ya existe un acta con ese número"));
        }
        return new BaseCheck(exists, errors);
    }

    @Override
    public boolean existsInBase(String naturalKey) {
        return sectionRecorder.sectionCodeExists(naturalKey);
    }

    @Override
    public Long record(Map<String, String> payload, Long institutionId) {
        ImportRowReader reader = new ImportRowReader(payload);
        Long sectionId = sectionRecorder.recordSection(new HistoricalSection(reader.text("sectionCode"),
                reader.text("subjectCode"), reader.integer("year", true, 1990, 2100),
                reader.enumValue("termType", TermType.class, true).orElseThrow(), reader.text("name"),
                reader.enumValue("shift", SectionShift.class, false).orElse(null),
                ImportValueParser.cleanDocumentNumber(payload.get("instructorDni"))));
        closureRecorder.recordClosure(new HistoricalClosure(sectionId, reader.date("closedOn", true),
                reader.text("actaNumber"), reader.text("actaBook"), reader.text("actaFolio")));
        return sectionId;
    }
}
