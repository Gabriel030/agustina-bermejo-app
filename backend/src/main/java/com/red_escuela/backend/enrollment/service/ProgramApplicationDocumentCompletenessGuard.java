package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.enrollment.exception.ApplicationDocumentsIncompleteException;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class ProgramApplicationDocumentCompletenessGuard {

    private final StudentDocumentRepository studentDocumentRepository;

    public ProgramApplicationDocumentCompletenessGuard(StudentDocumentRepository studentDocumentRepository) {
        this.studentDocumentRepository = studentDocumentRepository;
    }

    public void requireAllRequiredDocuments(Long studentId) {
        Set<DocumentType> missing = missingTypesOf(studentDocumentRepository.findByStudentId(studentId));
        if (!missing.isEmpty()) {
            throw new ApplicationDocumentsIncompleteException(studentId, namesOf(missing));
        }
    }

    public Set<DocumentType> missingTypesOf(Collection<StudentDocument> documents) {
        Set<DocumentType> missing = EnumSet.allOf(DocumentType.class);
        documents.stream().map(StudentDocument::getType).forEach(missing::remove);
        return missing;
    }

    private Set<String> namesOf(Set<DocumentType> types) {
        return types.stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
    }
}
