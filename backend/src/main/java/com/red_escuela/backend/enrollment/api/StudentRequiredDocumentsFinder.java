package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class StudentRequiredDocumentsFinder {

    private final StudentDocumentRepository studentDocumentRepository;

    public StudentRequiredDocumentsFinder(StudentDocumentRepository studentDocumentRepository) {
        this.studentDocumentRepository = studentDocumentRepository;
    }

    public Map<Long, StudentDocumentsStanding> findStandingOf(Collection<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Set<DocumentType>> presentByStudent = studentDocumentRepository.findByStudentIdIn(studentIds).stream()
                .collect(Collectors.groupingBy(document -> document.getStudent().getId(),
                        Collectors.mapping(StudentDocument::getType, Collectors.toCollection(() -> EnumSet.noneOf(DocumentType.class)))));
        Map<Long, StudentDocumentsStanding> standings = new HashMap<>();
        for (Long studentId : studentIds) {
            Set<DocumentType> missing = EnumSet.allOf(DocumentType.class);
            missing.removeAll(presentByStudent.getOrDefault(studentId, Set.of()));
            Set<String> missingNames = missing.stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
            standings.put(studentId, new StudentDocumentsStanding(missing.isEmpty(), missingNames));
        }
        return standings;
    }
}
