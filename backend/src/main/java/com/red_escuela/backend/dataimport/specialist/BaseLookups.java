package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.enrollment.api.StudentProgram;
import com.red_escuela.backend.enrollment.api.StudentSection;
import com.red_escuela.backend.identity.api.DocumentKey;
import com.red_escuela.backend.shared.domain.DocumentType;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record BaseLookups(boolean loaded, Map<DocumentKey, Long> studentIds, Map<String, Long> programIds,
        Map<String, Long> sectionIds, Map<StudentProgram, LocalDate> programEnrollmentDates,
        Set<StudentSection> sectionEnrollments) {
    public static final BaseLookups NONE = new BaseLookups(false, Map.of(), Map.of(), Map.of(), Map.of(), Set.of());

    public BaseLookups {
        studentIds = Map.copyOf(studentIds);
        programIds = Map.copyOf(programIds);
        sectionIds = Map.copyOf(sectionIds);
        programEnrollmentDates = Map.copyOf(programEnrollmentDates);
        sectionEnrollments = Set.copyOf(sectionEnrollments);
    }

    public Optional<LocalDate> programEnrollmentDate(DocumentType type, String number, String programName) {
        Long studentId = studentIds.get(new DocumentKey(type, number));
        Long programId = programIds.get(programName);
        if (studentId == null || programId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(programEnrollmentDates.get(new StudentProgram(studentId, programId)));
    }

    public boolean programEnrollmentExists(DocumentType type, String number, String programName) {
        return programEnrollmentDate(type, number, programName).isPresent();
    }

    public boolean sectionEnrollmentExists(DocumentType type, String number, String sectionCode) {
        Long studentId = studentIds.get(new DocumentKey(type, number));
        Long sectionId = sectionIds.get(sectionCode);
        return studentId != null && sectionId != null && sectionEnrollments.contains(new StudentSection(studentId, sectionId));
    }
}
