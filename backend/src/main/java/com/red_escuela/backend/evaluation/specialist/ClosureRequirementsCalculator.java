package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.api.SectionFreeByAbsencesFinder;
import com.red_escuela.backend.enrollment.api.PrerequisiteBatchQuery;
import com.red_escuela.backend.enrollment.api.PrerequisiteBatchResult;
import com.red_escuela.backend.enrollment.api.ProgramPrerequisiteBatchEvaluator;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.StudentDocumentsStanding;
import com.red_escuela.backend.enrollment.api.StudentRequiredDocumentsFinder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.api.PaymentsStanding;
import com.red_escuela.backend.evaluation.api.StudentPaymentsStandingPort;
import com.red_escuela.backend.evaluation.domain.ClosureAlert;
import com.red_escuela.backend.evaluation.domain.ClosureStudentAssessment;
import com.red_escuela.backend.evaluation.domain.CourseworkOutcome;
import com.red_escuela.backend.evaluation.domain.PrerequisiteGatedOutcome;
import com.red_escuela.backend.evaluation.entity.ClosureFinalCondition;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.policy.ClosureConditionPolicy;
import com.red_escuela.backend.evaluation.policy.CourseworkOutcomeCalculator;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.shared.domain.Grade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Service
public class ClosureRequirementsCalculator {

    private final CourseworkOutcomeCalculator outcomeCalculator = new CourseworkOutcomeCalculator();
    private final ClosureConditionPolicy closureConditionPolicy = new ClosureConditionPolicy();
    private final SectionFinder sectionFinder;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final SectionFreeByAbsencesFinder sectionFreeByAbsencesFinder;
    private final GradeItemRepository gradeItemRepository;
    private final PublishedGradeItemSelector publishedGradeItemSelector;
    private final ProgramPrerequisiteBatchEvaluator prerequisiteBatchEvaluator;
    private final StudentRequiredDocumentsFinder studentRequiredDocumentsFinder;
    private final StudentPaymentsStandingPort studentPaymentsStandingPort;
    private final Clock clock;

    public ClosureRequirementsCalculator(SectionFinder sectionFinder, SectionEnrollmentFinder sectionEnrollmentFinder,
            SectionFreeByAbsencesFinder sectionFreeByAbsencesFinder, GradeItemRepository gradeItemRepository,
            PublishedGradeItemSelector publishedGradeItemSelector,
            ProgramPrerequisiteBatchEvaluator prerequisiteBatchEvaluator,
            StudentRequiredDocumentsFinder studentRequiredDocumentsFinder,
            StudentPaymentsStandingPort studentPaymentsStandingPort, Clock clock) {
        this.sectionFinder = sectionFinder;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.sectionFreeByAbsencesFinder = sectionFreeByAbsencesFinder;
        this.gradeItemRepository = gradeItemRepository;
        this.publishedGradeItemSelector = publishedGradeItemSelector;
        this.prerequisiteBatchEvaluator = prerequisiteBatchEvaluator;
        this.studentRequiredDocumentsFinder = studentRequiredDocumentsFinder;
        this.studentPaymentsStandingPort = studentPaymentsStandingPort;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ClosureStudentAssessment> calculate(SectionKind kind, Long sectionId) {
        return kind == SectionKind.PROGRAM ? calculateProgramSection(sectionId) : calculateCourseSection(sectionId);
    }

    private List<ClosureStudentAssessment> calculateProgramSection(Long sectionId) {
        SubjectSection section = sectionFinder.findProgramSectionEntity(sectionId);
        List<SubjectSectionEnrollment> enrollments = sectionEnrollmentFinder.findMembersWithStudentInProgramSection(sectionId);
        List<Long> enrollmentIds = enrollments.stream().map(SubjectSectionEnrollment::getId).toList();
        Map<Long, List<GradeItem>> items = groupByEnrollment(
                enrollmentIds.isEmpty() ? List.of() : gradeItemRepository.findByProgramSubjectSectionEnrollmentIdIn(enrollmentIds),
                item -> item.getProgramSubjectSectionEnrollment().getId());
        Set<Long> free = sectionFreeByAbsencesFinder.findProgramEnrollmentIdsFreeByAbsences(sectionId, section.getMaxAbsenceRate());
        Map<Long, PrerequisiteBatchResult> prerequisites = prerequisiteBatchEvaluator.assessAll(enrollments.stream()
                .map(enrollment -> new PrerequisiteBatchQuery(
                        enrollment.getId(), enrollment.getProgramEnrollment().getId(), enrollment.subjectId()))
                .toList());
        List<EnrollmentRow> rows = enrollments.stream()
                .map(enrollment -> new EnrollmentRow(enrollment.getId(), enrollment.getStudent().getId(),
                        enrollment.getStatus(), enrollment.getFinalGradeType()))
                .toList();
        return assess(rows, SectionKind.PROGRAM, sectionId, section.getEvaluationScheme(),
                section.getSubject().getMaxFinalExamAttempts(), items, free, prerequisites);
    }

    private List<ClosureStudentAssessment> calculateCourseSection(Long sectionId) {
        CourseSubjectSection section = sectionFinder.findCourseSectionEntity(sectionId);
        List<CourseSubjectSectionEnrollment> enrollments = sectionEnrollmentFinder.findMembersWithStudentInCourseSection(sectionId);
        List<Long> enrollmentIds = enrollments.stream().map(CourseSubjectSectionEnrollment::getId).toList();
        Map<Long, List<GradeItem>> items = groupByEnrollment(
                enrollmentIds.isEmpty() ? List.of() : gradeItemRepository.findByCourseSubjectSectionEnrollmentIdIn(enrollmentIds),
                item -> item.getCourseSubjectSectionEnrollment().getId());
        Set<Long> free = sectionFreeByAbsencesFinder.findCourseEnrollmentIdsFreeByAbsences(sectionId, section.getMaxAbsenceRate());
        List<EnrollmentRow> rows = enrollments.stream()
                .map(enrollment -> new EnrollmentRow(enrollment.getId(), enrollment.getStudent().getId(),
                        enrollment.getStatus(), enrollment.getFinalGradeType()))
                .toList();
        return assess(rows, SectionKind.COURSE, sectionId, section.getEvaluationScheme(),
                section.getCourseSubject().getMaxFinalExamAttempts(), items, free, Map.of());
    }

    private List<ClosureStudentAssessment> assess(List<EnrollmentRow> rows, SectionKind kind, Long sectionId,
            EvaluationScheme scheme, Integer maxFinalExamAttempts, Map<Long, List<GradeItem>> itemsByEnrollment,
            Set<Long> freeEnrollmentIds, Map<Long, PrerequisiteBatchResult> prerequisites) {
        List<Long> studentIds = rows.stream().map(EnrollmentRow::studentId).distinct().toList();
        Map<Long, PaymentsStanding> payments = studentPaymentsStandingPort.findPaymentsStanding(studentIds, LocalDate.now(clock));
        Map<Long, StudentDocumentsStanding> documents = studentRequiredDocumentsFinder.findStandingOf(studentIds);
        Map<Long, List<GradeItem>> published = publishedGradeItemSelector.selectPublishedByEnrollment(
                kind, sectionId, itemsByEnrollment);
        List<ClosureStudentAssessment> assessments = new ArrayList<>();
        for (EnrollmentRow row : rows) {
            assessments.add(assessRow(row, scheme, maxFinalExamAttempts, published.getOrDefault(row.enrollmentId(), List.of()),
                    freeEnrollmentIds.contains(row.enrollmentId()), Optional.ofNullable(prerequisites.get(row.enrollmentId())),
                    payments.getOrDefault(row.studentId(), PaymentsStanding.NOT_VERIFIED),
                    documents.get(row.studentId()).complete()));
        }
        return assessments;
    }

    private ClosureStudentAssessment assessRow(EnrollmentRow row, EvaluationScheme scheme, Integer maxFinalExamAttempts,
            List<GradeItem> items, boolean free, Optional<PrerequisiteBatchResult> prerequisites,
            PaymentsStanding payments, boolean documentsComplete) {
        boolean prerequisitesMet = prerequisites.map(PrerequisiteBatchResult::met).orElse(true);
        Boolean correlativesOk = prerequisites.map(PrerequisiteBatchResult::met).orElse(null);
        EnrollmentStatus current = CourseworkOutcomeCalculator.isRebuildable(row.status(), row.finalGradeType())
                ? EnrollmentStatus.ENROLLED : row.status();
        Optional<CourseworkOutcome> calculated = scheme == null ? Optional.empty()
                : outcomeCalculator.calculate(scheme, items, current, maxFinalExamAttempts);
        PrerequisiteGatedOutcome gated = outcomeCalculator.promotionPolicy()
                .restrictApprovalWhenPrerequisitesUnmet(calculated, () -> prerequisitesMet);
        boolean promotionCandidate = calculated.filter(CourseworkOutcome::isPromotion).isPresent();
        var finalCondition = closureConditionPolicy.decideFinalCondition(
                gated.outcome(), gated.forcedFinalCondition().isPresent(), current, free, payments, documentsComplete);
        Grade promotionGrade = finalCondition
                .filter(condition -> condition == ClosureFinalCondition.PROMOTES
                        || condition == ClosureFinalCondition.PROMOTION_CONDITIONAL)
                .flatMap(condition -> gated.outcome())
                .map(CourseworkOutcome::finalScore)
                .orElse(null);
        List<ClosureAlert> alerts = gated.alerts();
        return new ClosureStudentAssessment(row.enrollmentId(), row.studentId(), finalCondition, promotionGrade,
                !free, promotionCandidate, payments, documentsComplete, correlativesOk, prerequisites, alerts);
    }

    private Map<Long, List<GradeItem>> groupByEnrollment(List<GradeItem> items, Function<GradeItem, Long> enrollmentIdOf) {
        Map<Long, List<GradeItem>> byEnrollment = new HashMap<>();
        items.forEach(item -> byEnrollment.computeIfAbsent(enrollmentIdOf.apply(item), key -> new ArrayList<>()).add(item));
        return byEnrollment;
    }

    private record EnrollmentRow(Long enrollmentId, Long studentId, EnrollmentStatus status, FinalGradeType finalGradeType) {
    }
}
