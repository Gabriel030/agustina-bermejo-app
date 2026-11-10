package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse.DocumentationSummary;
import com.red_escuela.backend.enrollment.dto.StudentDocumentResponse;
import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProgramApplicationReviewViews {

    private final StudentDocumentRepository studentDocumentRepository;

    private final ProgramApplicationDocumentCompletenessGuard completenessGuard;

    public ProgramApplicationReviewViews(StudentDocumentRepository studentDocumentRepository,
                                         ProgramApplicationDocumentCompletenessGuard completenessGuard) {
        this.studentDocumentRepository = studentDocumentRepository;
        this.completenessGuard = completenessGuard;
    }

    @Transactional(readOnly = true)
    public List<ProgramEnrollmentApplicationResponse> viewsOf(List<ProgramEnrollmentApplication> applications) {
        Set<Long> studentIds = applications.stream().map(application -> application.getStudent().getId()).collect(Collectors.toSet());
        Map<Long, List<StudentDocument>> documentsByStudentId = studentIds.isEmpty() ? Map.of()
                : studentDocumentRepository.findByStudentIdIn(studentIds).stream()
                .collect(Collectors.groupingBy(document -> document.getStudent().getId()));
        return applications.stream()
                .map(application -> ProgramEnrollmentApplicationResponse.forReview(application,
                        documentationOf(documentsByStudentId.getOrDefault(application.getStudent().getId(), List.of()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgramEnrollmentApplicationResponse viewOf(ProgramEnrollmentApplication application) {
        return viewsOf(List.of(application)).getFirst();
    }

    @Transactional(readOnly = true)
    public DocumentationSummary documentationOf(Long studentId) {
        return documentationOf(studentDocumentRepository.findByStudentId(studentId));
    }

    private DocumentationSummary documentationOf(Collection<StudentDocument> documents) {
        List<DocumentType> missingTypes = completenessGuard.missingTypesOf(documents).stream().sorted().toList();
        List<StudentDocumentResponse> summaries = documents.stream()
                .sorted(Comparator.comparing(StudentDocument::getType))
                .map(StudentDocumentResponse::from)
                .toList();
        return new DocumentationSummary(missingTypes.isEmpty(), missingTypes, summaries);
    }
}
