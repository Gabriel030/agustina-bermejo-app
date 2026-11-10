package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse.DocumentationSummary;
import com.red_escuela.backend.enrollment.dto.StudentDocumentResponse;
import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.enrollment.repository.ApplicationCohortName;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
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
    private final ProgramEnrollmentApplicationRepository applicationRepository;
    private final ProgramEnrollmentApplicationFinder applicationFinder;
    private final ProgramApplicationDocumentCompletenessGuard completenessGuard;

    public ProgramApplicationReviewViews(StudentDocumentRepository studentDocumentRepository,
                                         ProgramEnrollmentApplicationRepository applicationRepository,
                                         ProgramEnrollmentApplicationFinder applicationFinder,
                                         ProgramApplicationDocumentCompletenessGuard completenessGuard) {
        this.studentDocumentRepository = studentDocumentRepository;
        this.applicationRepository = applicationRepository;
        this.applicationFinder = applicationFinder;
        this.completenessGuard = completenessGuard;
    }

    @Transactional(readOnly = true)
    public List<ProgramEnrollmentApplicationResponse> pendingViews() {
        return viewsOf(applicationRepository.findByStatusOrderByRequestedAtAsc(ProgramEnrollmentApplicationStatus.PENDING));
    }

    @Transactional(readOnly = true)
    public ProgramEnrollmentApplicationResponse viewOfApplication(Long applicationId) {
        return viewsOf(List.of(applicationFinder.findOrThrow(applicationId))).getFirst();
    }

    @Transactional(readOnly = true)
    public List<ProgramEnrollmentApplicationResponse> viewsOf(List<ProgramEnrollmentApplication> applications) {
        if (applications.isEmpty()) {
            return List.of();
        }
        Set<Long> studentIds = applications.stream().map(application -> application.getStudent().getId()).collect(Collectors.toSet());
        Map<Long, List<StudentDocument>> documentsByStudentId = studentDocumentRepository.findByStudentIdIn(studentIds).stream()
                .collect(Collectors.groupingBy(document -> document.getStudent().getId()));
        Map<Long, String> cohortNameByApplicationId = applicationRepository
                .findCohortNamesByApplicationIds(applications.stream().map(ProgramEnrollmentApplication::getId).toList()).stream()
                .collect(Collectors.toMap(ApplicationCohortName::getApplicationId, ApplicationCohortName::getName));
        return applications.stream()
                .map(application -> ProgramEnrollmentApplicationResponse.forReview(application,
                        documentationOf(documentsByStudentId.getOrDefault(application.getStudent().getId(), List.of())),
                        cohortNameByApplicationId.get(application.getId())))
                .toList();
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
