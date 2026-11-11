package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.SectionInstructorFinder;
import com.red_escuela.backend.academicstructure.api.SectionTermEndFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.api.SectionPresencePercentFinder;
import com.red_escuela.backend.enrollment.api.PrerequisiteOutcome;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.StudentDocumentsStanding;
import com.red_escuela.backend.enrollment.api.StudentRequiredDocumentsFinder;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.domain.ClosureAlert;
import com.red_escuela.backend.evaluation.domain.ClosureBlockedReason;
import com.red_escuela.backend.evaluation.domain.ClosureStudentAssessment;
import com.red_escuela.backend.evaluation.domain.ClosureSuggestedResult;
import com.red_escuela.backend.evaluation.domain.ClosureViewerProfile;
import com.red_escuela.backend.evaluation.dto.ClosureSheetAdministrativeResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetAvailabilityResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetColumnResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetGradeResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetRequirementsResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetSchemeResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetStudentResponse;
import com.red_escuela.backend.evaluation.dto.ClosureSheetSummaryResponse;
import com.red_escuela.backend.evaluation.dto.SectionInstructorSummary;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.entity.CommissionClosureStudent;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.repository.CommissionClosureStudentRepository;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ClosureSheetAssembler {

    private final SectionFinder sectionFinder;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final SectionInstructorFinder sectionInstructorFinder;
    private final SectionTermEndFinder sectionTermEndFinder;
    private final SectionPresencePercentFinder sectionPresencePercentFinder;
    private final StudentRequiredDocumentsFinder studentRequiredDocumentsFinder;
    private final GradeItemRepository gradeItemRepository;
    private final PublishedGradeItemSelector publishedGradeItemSelector;
    private final CommissionClosureStudentRepository closureStudentRepository;
    private final ClosureColumnsFinder closureColumnsFinder;

    public ClosureSheetAssembler(SectionFinder sectionFinder, SectionEnrollmentFinder sectionEnrollmentFinder,
            SectionInstructorFinder sectionInstructorFinder, SectionTermEndFinder sectionTermEndFinder,
            SectionPresencePercentFinder sectionPresencePercentFinder,
            StudentRequiredDocumentsFinder studentRequiredDocumentsFinder, GradeItemRepository gradeItemRepository,
            PublishedGradeItemSelector publishedGradeItemSelector,
            CommissionClosureStudentRepository closureStudentRepository, ClosureColumnsFinder closureColumnsFinder) {
        this.sectionFinder = sectionFinder;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.sectionInstructorFinder = sectionInstructorFinder;
        this.sectionTermEndFinder = sectionTermEndFinder;
        this.sectionPresencePercentFinder = sectionPresencePercentFinder;
        this.studentRequiredDocumentsFinder = studentRequiredDocumentsFinder;
        this.gradeItemRepository = gradeItemRepository;
        this.publishedGradeItemSelector = publishedGradeItemSelector;
        this.closureStudentRepository = closureStudentRepository;
        this.closureColumnsFinder = closureColumnsFinder;
    }

    @Transactional(readOnly = true)
    public ClosureSheetResponse assemble(CommissionClosure closure, List<ClosureStudentAssessment> assessments,
            ClosureViewerProfile viewer) {
        SectionKind kind = closure.getSectionKind();
        Long sectionId = closure.getSectionId();
        SectionContext context = contextOf(kind, sectionId);
        List<ClosureSheetColumnResponse> columns = closureColumnsFinder.findColumns(kind, sectionId);
        Map<Long, CommissionClosureStudent> snapshotRows = closureStudentRepository.findByClosureId(closure.getId()).stream()
                .collect(Collectors.toMap(CommissionClosureStudent::getEnrollmentId, Function.identity()));
        Map<Long, Percentage> presence = sectionPresencePercentFinder.findPresencePercentByEnrollment(kind, sectionId);
        Map<Long, StudentDocumentsStanding> documents = viewer.seesAdministrativeDetail()
                ? studentRequiredDocumentsFinder.findStandingOf(
                        assessments.stream().map(ClosureStudentAssessment::studentId).distinct().toList())
                : Map.of();
        Map<Long, List<GradeItem>> published = publishedGradeItemSelector.selectPublishedByEnrollment(
                kind, sectionId, context.itemsByEnrollment());
        List<ClosureSheetStudentResponse> students = assessments.stream()
                .map(assessment -> studentRow(assessment, context, columns, published, snapshotRows, presence, documents,
                        viewer, closure))
                .sorted(Comparator.comparing((ClosureSheetStudentResponse row) -> row.lastname().toLowerCase())
                        .thenComparing(row -> row.name().toLowerCase()))
                .toList();
        return new ClosureSheetResponse(closure.getId(), sectionId, context.sectionName(), context.subjectName(),
                context.programName(), context.termLabel(), sectionTermEndFinder.termEndDate(kind, sectionId),
                instructorsOf(kind, sectionId), closure.getStatus(),
                availabilityOf(closure, columns, viewer), schemeOf(context), columns, students, summaryOf(students),
                closure.getReportNumber(), closure.getClosedAt());
    }

    private ClosureSheetAvailabilityResponse availabilityOf(CommissionClosure closure,
            List<ClosureSheetColumnResponse> columns, ClosureViewerProfile viewer) {
        if (closure.getStatus() == ClosureStatus.CLOSED) {
            return new ClosureSheetAvailabilityResponse(false, false, ClosureBlockedReason.ALREADY_CLOSED);
        }
        if (closure.getStatus() == ClosureStatus.TEACHER_CONFIRMED) {
            return new ClosureSheetAvailabilityResponse(false, false, ClosureBlockedReason.ALREADY_CONFIRMED);
        }
        if (!closureColumnsFinder.allPublished(columns)) {
            return new ClosureSheetAvailabilityResponse(false, false, ClosureBlockedReason.PUBLISHED_GRADES_INCOMPLETE);
        }
        return new ClosureSheetAvailabilityResponse(viewer == ClosureViewerProfile.TEACHER, false, null);
    }

    private ClosureSheetSchemeResponse schemeOf(SectionContext context) {
        EvaluationScheme scheme = context.scheme();
        if (scheme == null) {
            return new ClosureSheetSchemeResponse(null, null, null, 0, 0, 0, context.maxAbsenceRate());
        }
        return new ClosureSheetSchemeResponse(scheme.getMinRegularGrade(), scheme.getMinApprovalGrade(),
                scheme.getPromotionMinGrade(), countOf(scheme.getExamCountExpected()), countOf(scheme.getTpCountExpected()),
                scheme.getRecoveryQuota(), context.maxAbsenceRate());
    }

    private List<SectionInstructorSummary> instructorsOf(SectionKind kind, Long sectionId) {
        return (kind == SectionKind.PROGRAM
                ? sectionInstructorFinder.findProgramSectionInstructors(sectionId)
                : sectionInstructorFinder.findCourseSectionInstructors(sectionId)).stream()
                .map(SectionInstructorSummary::from)
                .toList();
    }

    private ClosureSheetStudentResponse studentRow(ClosureStudentAssessment assessment, SectionContext context,
            List<ClosureSheetColumnResponse> columns, Map<Long, List<GradeItem>> published,
            Map<Long, CommissionClosureStudent> snapshotRows, Map<Long, Percentage> presence,
            Map<Long, StudentDocumentsStanding> documents, ClosureViewerProfile viewer, CommissionClosure closure) {
        Long enrollmentId = assessment.enrollmentId();
        EnrollmentRow enrollment = context.enrollmentsById().get(enrollmentId);
        List<GradeItem> items = published.getOrDefault(enrollmentId, List.of());
        List<ClosureSheetGradeResponse> grades = gradesOf(columns, items);
        Boolean coursework = courseworkOf(context.scheme(), items);
        CommissionClosureStudent snapshot = snapshotRows.get(enrollmentId);
        ClosureSuggestedResult result = !closure.isOpenForTeacher() && snapshot != null
                ? ClosureSuggestedResult.valueOf(snapshot.getFinalCondition().name())
                : ClosureSuggestedResult.of(assessment.finalCondition());
        return new ClosureSheetStudentResponse(enrollmentId, enrollment.person().getLastname(), enrollment.person().getName(),
                enrollment.person().getDni(), enrollment.status(), grades,
                (int) items.stream().filter(item -> item.getType() == GradeItemType.EXAM && item.isRecovered()).count(),
                averageOf(context.scheme(), items).orElse(null), presence.get(enrollmentId), result,
                new ClosureSheetRequirementsResponse(coursework, assessment.correlativesOk(), assessment.attendanceOk(),
                        assessment.gradeOk()),
                administrativeOf(assessment, documents, viewer), alertsOf(assessment, context.scheme(), items, coursework),
                assessment.prerequisites().map(prerequisites -> prerequisites.outcome() == PrerequisiteOutcome.WAIVED)
                        .orElse(false),
                snapshot == null ? null : snapshot.getTeacherObservation());
    }

    private ClosureSheetAdministrativeResponse administrativeOf(ClosureStudentAssessment assessment,
            Map<Long, StudentDocumentsStanding> documents, ClosureViewerProfile viewer) {
        if (!viewer.seesAdministrativeDetail()) {
            return new ClosureSheetAdministrativeResponse(assessment.payments(), assessment.documentsComplete(), null);
        }
        StudentDocumentsStanding standing = documents.get(assessment.studentId());
        List<String> missing = standing == null ? List.of() : standing.missingDocuments().stream().sorted().toList();
        return new ClosureSheetAdministrativeResponse(assessment.payments(), assessment.documentsComplete(), missing);
    }

    private List<ClosureSheetGradeResponse> gradesOf(List<ClosureSheetColumnResponse> columns, List<GradeItem> items) {
        List<ClosureSheetGradeResponse> grades = new ArrayList<>();
        for (ClosureSheetColumnResponse column : columns) {
            Optional<GradeItem> item = items.stream()
                    .filter(candidate -> candidate.getType() == column.type()
                            && candidate.getSequenceNumber() != null && candidate.getSequenceNumber() == column.sequenceNumber())
                    .findFirst();
            boolean recovered = item.map(GradeItem::isRecovered).orElse(false);
            Grade shown = item.map(found -> recovered ? found.getOriginalScore() : found.getScore()).orElse(null);
            Grade retake = recovered ? item.get().getScore() : null;
            grades.add(new ClosureSheetGradeResponse(column.type(), column.sequenceNumber(),
                    column.published() ? shown : null, column.published() ? retake : null, column.published()));
        }
        return grades;
    }

    private Optional<Grade> averageOf(EvaluationScheme scheme, List<GradeItem> items) {
        List<Grade> exams = items.stream().filter(item -> item.getType() == GradeItemType.EXAM && item.getScore() != null)
                .map(GradeItem::getScore).toList();
        if (scheme == null || exams.size() < countOf(scheme.getExamCountExpected()) || exams.isEmpty()) {
            return Optional.empty();
        }
        return Grade.average(exams);
    }

    private Boolean courseworkOf(EvaluationScheme scheme, List<GradeItem> items) {
        int expected = scheme == null ? 0 : countOf(scheme.getTpCountExpected());
        if (expected == 0 || scheme.getMinRegularGrade() == null) {
            return null;
        }
        List<Grade> scores = items.stream().filter(item -> item.getType() == GradeItemType.TP && item.getScore() != null)
                .map(GradeItem::getScore).toList();
        if (scores.size() < expected) {
            return null;
        }
        return scores.stream().allMatch(score -> score.meets(scheme.getMinRegularGrade()));
    }

    private List<ClosureAlert> alertsOf(ClosureStudentAssessment assessment, EvaluationScheme scheme, List<GradeItem> items,
            Boolean coursework) {
        List<ClosureAlert> alerts = new ArrayList<>(assessment.alerts());
        boolean hasExamScore = items.stream().anyMatch(item -> item.getType() == GradeItemType.EXAM && item.getScore() != null);
        boolean requiresCoursework = scheme != null && countOf(scheme.getTpCountExpected()) > 0;
        if (requiresCoursework && hasExamScore && !Boolean.TRUE.equals(coursework)) {
            alerts.add(ClosureAlert.EXAM_WITHOUT_COURSEWORK);
        }
        return alerts;
    }

    private ClosureSheetSummaryResponse summaryOf(List<ClosureSheetStudentResponse> students) {
        Map<ClosureSuggestedResult, Long> counts = students.stream()
                .collect(Collectors.groupingBy(ClosureSheetStudentResponse::suggestedResult, Collectors.counting()));
        return new ClosureSheetSummaryResponse(count(counts, ClosureSuggestedResult.PROMOTES),
                count(counts, ClosureSuggestedResult.GOES_TO_FINAL), count(counts, ClosureSuggestedResult.FAILED),
                count(counts, ClosureSuggestedResult.FREE), count(counts, ClosureSuggestedResult.PENDING),
                count(counts, ClosureSuggestedResult.PROMOTION_CONDITIONAL));
    }

    private int count(Map<ClosureSuggestedResult, Long> counts, ClosureSuggestedResult result) {
        return counts.getOrDefault(result, 0L).intValue();
    }

    private int countOf(Integer value) {
        return value == null ? 0 : value;
    }

    private SectionContext contextOf(SectionKind kind, Long sectionId) {
        if (kind == SectionKind.PROGRAM) {
            SubjectSection section = sectionFinder.findProgramSectionEntity(sectionId);
            var enrollments = sectionEnrollmentFinder.findMembersWithStudentInProgramSection(sectionId);
            List<Long> ids = enrollments.stream().map(enrollment -> enrollment.getId()).toList();
            Map<Long, List<GradeItem>> items = groupByEnrollment(
                    ids.isEmpty() ? List.of() : gradeItemRepository.findByProgramSubjectSectionEnrollmentIdIn(ids),
                    item -> item.getProgramSubjectSectionEnrollment().getId());
            Map<Long, EnrollmentRow> rows = enrollments.stream().collect(Collectors.toMap(enrollment -> enrollment.getId(),
                    enrollment -> new EnrollmentRow(enrollment.getStudent().getPerson(), enrollment.getStatus())));
            AcademicOfferingCycle cycle = section.getAcademicOfferingCycle();
            return new SectionContext(section.getName(), section.getSubject().getName(),
                    cycle == null || cycle.getProgram() == null ? null : cycle.getProgram().getName(),
                    section.getTerm() == null ? null : section.getTerm().getDescription(), section.getEvaluationScheme(),
                    section.getMaxAbsenceRate(), rows, items);
        }
        CourseSubjectSection section = sectionFinder.findCourseSectionEntity(sectionId);
        var enrollments = sectionEnrollmentFinder.findMembersWithStudentInCourseSection(sectionId);
        List<Long> ids = enrollments.stream().map(enrollment -> enrollment.getId()).toList();
        Map<Long, List<GradeItem>> items = groupByEnrollment(
                ids.isEmpty() ? List.of() : gradeItemRepository.findByCourseSubjectSectionEnrollmentIdIn(ids),
                item -> item.getCourseSubjectSectionEnrollment().getId());
        Map<Long, EnrollmentRow> rows = enrollments.stream().collect(Collectors.toMap(enrollment -> enrollment.getId(),
                enrollment -> new EnrollmentRow(enrollment.getStudent().getPerson(), enrollment.getStatus())));
        AcademicOfferingCycle cycle = section.getAcademicOfferingCycle();
        return new SectionContext(section.getName(), section.getCourseSubject().getName(),
                cycle == null || cycle.getCourse() == null ? null : cycle.getCourse().getName(),
                section.getTerm() == null ? null : section.getTerm().getDescription(), section.getEvaluationScheme(),
                section.getMaxAbsenceRate(), rows, items);
    }

    private Map<Long, List<GradeItem>> groupByEnrollment(List<GradeItem> items, Function<GradeItem, Long> enrollmentIdOf) {
        Map<Long, List<GradeItem>> byEnrollment = new HashMap<>();
        items.forEach(item -> byEnrollment.computeIfAbsent(enrollmentIdOf.apply(item), key -> new ArrayList<>()).add(item));
        return byEnrollment;
    }

    private record EnrollmentRow(Person person, EnrollmentStatus status) {
    }

    private record SectionContext(String sectionName, String subjectName, String programName, String termLabel,
            EvaluationScheme scheme, Percentage maxAbsenceRate, Map<Long, EnrollmentRow> enrollmentsById,
            Map<Long, List<GradeItem>> itemsByEnrollment) {
    }
}
