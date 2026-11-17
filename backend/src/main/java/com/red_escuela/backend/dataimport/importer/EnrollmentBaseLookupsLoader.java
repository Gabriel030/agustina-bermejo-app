package com.red_escuela.backend.dataimport.importer;

import com.red_escuela.backend.academicstructure.api.HistoricalProgramRecorder;
import com.red_escuela.backend.academicstructure.api.HistoricalSectionRecorder;
import com.red_escuela.backend.dataimport.specialist.BaseLookups;
import com.red_escuela.backend.dataimport.specialist.ImportDocumentKey;
import com.red_escuela.backend.dataimport.specialist.ImportValueParser;
import com.red_escuela.backend.enrollment.api.HistoricalEnrollmentRecorder;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentRef;
import com.red_escuela.backend.enrollment.api.StudentProgram;
import com.red_escuela.backend.evaluation.api.HistoricalClosureRecorder;
import com.red_escuela.backend.enrollment.api.StudentSection;
import com.red_escuela.backend.identity.api.DocumentKey;
import com.red_escuela.backend.identity.api.HistoricalPersonRecorder;
import com.red_escuela.backend.shared.domain.DocumentType;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class EnrollmentBaseLookupsLoader {
    private final HistoricalPersonRecorder personRecorder;
    private final HistoricalProgramRecorder programRecorder;
    private final HistoricalSectionRecorder sectionRecorder;
    private final HistoricalEnrollmentRecorder enrollmentRecorder;
    private final HistoricalClosureRecorder closureRecorder;

    EnrollmentBaseLookupsLoader(HistoricalPersonRecorder personRecorder, HistoricalProgramRecorder programRecorder,
            HistoricalSectionRecorder sectionRecorder, HistoricalEnrollmentRecorder enrollmentRecorder,
            HistoricalClosureRecorder closureRecorder) {
        this.closureRecorder = closureRecorder;
        this.personRecorder = personRecorder;
        this.programRecorder = programRecorder;
        this.sectionRecorder = sectionRecorder;
        this.enrollmentRecorder = enrollmentRecorder;
    }

    BaseLookups load(List<Map<String, String>> payloads, boolean withSections) {
        return load(payloads, withSections, false);
    }

    BaseLookups loadForRecord(List<Map<String, String>> payloads, boolean withSections) {
        return load(payloads, withSections, true);
    }

    private BaseLookups load(List<Map<String, String>> payloads, boolean withSections, boolean forRecord) {
        Set<DocumentKey> documents = new HashSet<>();
        Set<String> programNames = new HashSet<>();
        Set<String> sectionCodes = new HashSet<>();
        for (Map<String, String> payload : payloads) {
            documents.add(documentOf(payload));
            programNames.add(ImportValueParser.cleanText(payload.get("programName")));
            if (withSections) {
                sectionCodes.add(ImportValueParser.cleanText(payload.get("sectionCode")));
            }
        }
        Map<DocumentKey, Long> studentIds = personRecorder.findStudentIdsByDocuments(documents);
        Map<String, Long> programIds = programRecorder.findProgramIdsByNames(programNames);
        Map<String, Long> sectionIds = withSections ? sectionRecorder.findSectionIdsByCodes(sectionCodes) : Map.of();
        Set<StudentProgram> programPairs = new HashSet<>();
        Set<StudentSection> sectionPairs = new HashSet<>();
        for (Map<String, String> payload : payloads) {
            Long studentId = studentIds.get(documentOf(payload));
            Long programId = programIds.get(ImportValueParser.cleanText(payload.get("programName")));
            if (studentId != null && programId != null) {
                programPairs.add(new StudentProgram(studentId, programId));
            }
            Long sectionId = withSections ? sectionIds.get(ImportValueParser.cleanText(payload.get("sectionCode"))) : null;
            if (studentId != null && sectionId != null) {
                sectionPairs.add(new StudentSection(studentId, sectionId));
            }
        }
        Map<StudentProgram, ProgramEnrollmentRef> programEnrollments = enrollmentRecorder.findProgramEnrollmentRefs(programPairs);
        if (forRecord) {
            Collection<Long> loadedSectionIds = sectionIds.values();
            return new BaseLookups(true, studentIds, programIds, sectionIds, programEnrollments, Set.of(),
                    withSections ? sectionRecorder.findTermStartsBySectionIds(loadedSectionIds) : Map.of(),
                    withSections ? closureRecorder.findClosureIdsBySectionIds(loadedSectionIds) : Map.of());
        }
        Set<StudentSection> sectionEnrollments = enrollmentRecorder.findExistingSectionEnrollments(sectionPairs);
        return new BaseLookups(true, studentIds, programIds, sectionIds, programEnrollments, sectionEnrollments, Map.of(),
                Map.of());
    }

    private static DocumentKey documentOf(Map<String, String> payload) {
        DocumentType type = ImportDocumentKey.typeOf(payload).orElse(DocumentType.DNI);
        return new DocumentKey(type, ImportDocumentKey.numberOf(payload, type));
    }
}
