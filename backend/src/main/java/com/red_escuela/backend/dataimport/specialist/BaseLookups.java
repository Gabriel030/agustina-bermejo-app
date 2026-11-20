package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.enrollment.api.ProgramEnrollmentRef;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentReferences;
import com.red_escuela.backend.enrollment.api.StudentProgram;
import com.red_escuela.backend.enrollment.api.StudentSection;
import com.red_escuela.backend.identity.api.DocumentKey;
import com.red_escuela.backend.shared.domain.DocumentType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record BaseLookups(boolean loaded, Map<DocumentKey, Long> studentIds, Map<String, Long> programIds,
        Map<String, Long> sectionIds, Map<StudentProgram, ProgramEnrollmentRef> programEnrollments,
        Set<StudentSection> sectionEnrollments, Map<Long, LocalDateTime> sectionTermStarts, Map<Long, Long> closureIds) {
    public static final BaseLookups NONE = new BaseLookups(false, Map.of(), Map.of(), Map.of(), Map.of(), Set.of(), Map.of(),
            Map.of());

    public BaseLookups {
        studentIds = Map.copyOf(studentIds);
        programIds = Map.copyOf(programIds);
        sectionIds = Map.copyOf(sectionIds);
        programEnrollments = Map.copyOf(programEnrollments);
        sectionEnrollments = Set.copyOf(sectionEnrollments);
        sectionTermStarts = Map.copyOf(sectionTermStarts);
        closureIds = Map.copyOf(closureIds);
    }

    public Optional<LocalDate> programEnrollmentDate(DocumentType type, String number, String programName) {
        Long studentId = studentIds.get(new DocumentKey(type, number));
        Long programId = programIds.get(programName);
        if (studentId == null || programId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(programEnrollments.get(new StudentProgram(studentId, programId)))
                .map(ProgramEnrollmentRef::enrollmentDate);
    }

    public Optional<SectionEnrollmentReferences> sectionEnrollmentReferences(DocumentType type, String number,
            String programName, Long sectionId) {
        Long studentId = studentIds.get(new DocumentKey(type, number));
        Long programId = programIds.get(programName);
        LocalDateTime sectionTermStart = sectionTermStarts.get(sectionId);
        if (studentId == null || programId == null || sectionTermStart == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(programEnrollments.get(new StudentProgram(studentId, programId)))
                .map(ref -> new SectionEnrollmentReferences(studentId, ref.id(), ref.enrollmentDate(), sectionTermStart));
    }

    public Optional<Long> studentId(DocumentType type, String number) {
        return Optional.ofNullable(studentIds.get(new DocumentKey(type, number)));
    }

    public Optional<Long> programId(String programName) {
        return Optional.ofNullable(programIds.get(programName));
    }

    public Optional<Long> sectionId(String sectionCode) {
        return Optional.ofNullable(sectionIds.get(sectionCode));
    }

    public Optional<Long> closureIdOf(Long sectionId) {
        return Optional.ofNullable(closureIds.get(sectionId));
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
