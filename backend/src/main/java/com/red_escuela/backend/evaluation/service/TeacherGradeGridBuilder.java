package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.academicstructure.api.SectionTermEndFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.api.SectionFreeByAbsencesFinder;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.domain.GradeGridCondition;
import com.red_escuela.backend.evaluation.dto.GradeGridCellResponse;
import com.red_escuela.backend.evaluation.dto.GradeGridColumnResponse;
import com.red_escuela.backend.evaluation.dto.GradeGridStudentResponse;
import com.red_escuela.backend.evaluation.dto.TeacherGradeGridResponse;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemAudit;
import com.red_escuela.backend.evaluation.entity.GradeItemAuditKind;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.repository.GradeItemAuditRepository;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.evaluation.repository.SectionGradeItemPublicationRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TeacherGradeGridBuilder {

    private final SectionFinder sectionFinder;
    private final SectionTeachingFinder sectionTeachingFinder;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final SectionFreeByAbsencesFinder sectionFreeByAbsencesFinder;
    private final GradeItemRepository gradeItemRepository;
    private final SectionEvaluationConfigRepository configRepository;
    private final SectionGradeItemPublicationRepository publicationRepository;
    private final GradeItemAuditRepository auditRepository;
    private final SectionTermEndFinder sectionTermEndFinder;

    public TeacherGradeGridBuilder(SectionFinder sectionFinder,
                                   SectionTeachingFinder sectionTeachingFinder,
                                   SectionEnrollmentFinder sectionEnrollmentFinder,
                                   SectionFreeByAbsencesFinder sectionFreeByAbsencesFinder,
                                   GradeItemRepository gradeItemRepository,
                                   SectionEvaluationConfigRepository configRepository,
                                   SectionGradeItemPublicationRepository publicationRepository,
                                   GradeItemAuditRepository auditRepository,
                                   SectionTermEndFinder sectionTermEndFinder) {
        this.sectionFinder = sectionFinder;
        this.sectionTeachingFinder = sectionTeachingFinder;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.sectionFreeByAbsencesFinder = sectionFreeByAbsencesFinder;
        this.gradeItemRepository = gradeItemRepository;
        this.configRepository = configRepository;
        this.publicationRepository = publicationRepository;
        this.auditRepository = auditRepository;
        this.sectionTermEndFinder = sectionTermEndFinder;
    }

    @Transactional(readOnly = true)
    public TeacherGradeGridResponse buildForProgramSection(Long sectionId, Long instructorId) {
        sectionFinder.requireExists(SectionKind.PROGRAM, sectionId);
        sectionTeachingFinder.requireTeaches(SectionKind.PROGRAM, sectionId, instructorId);
        return programGrid(sectionId);
    }

    @Transactional(readOnly = true)
    public TeacherGradeGridResponse buildForProgramSectionAsStaff(Long sectionId) {
        sectionFinder.requireExists(SectionKind.PROGRAM, sectionId);
        return programGrid(sectionId);
    }

    @Transactional(readOnly = true)
    public TeacherGradeGridResponse buildForCourseSection(Long sectionId, Long instructorId) {
        sectionFinder.requireExists(SectionKind.COURSE, sectionId);
        sectionTeachingFinder.requireTeaches(SectionKind.COURSE, sectionId, instructorId);
        return courseGrid(sectionId);
    }

    @Transactional(readOnly = true)
    public TeacherGradeGridResponse buildForCourseSectionAsStaff(Long sectionId) {
        sectionFinder.requireExists(SectionKind.COURSE, sectionId);
        return courseGrid(sectionId);
    }

    private TeacherGradeGridResponse programGrid(Long sectionId) {
        SubjectSection section = sectionFinder.findProgramSectionEntity(sectionId);
        List<SubjectSectionEnrollment> enrollments = sectionEnrollmentFinder.findMembersWithStudentInProgramSection(sectionId);
        List<Long> enrollmentIds = enrollments.stream().map(SubjectSectionEnrollment::getId).toList();
        Map<Long, List<GradeItem>> itemsByEnrollment = groupByEnrollment(
                enrollmentIds.isEmpty() ? List.of() : gradeItemRepository.findByProgramSubjectSectionEnrollmentIdIn(enrollmentIds),
                item -> item.getProgramSubjectSectionEnrollment().getId());
        Set<Long> freeEnrollmentIds = sectionFreeByAbsencesFinder
                .findProgramEnrollmentIdsFreeByAbsences(sectionId, section.getMaxAbsenceRate());
        Optional<SectionEvaluationConfig> config = configRepository.findBySubjectSectionId(sectionId);
        Map<String, LocalDateTime> publications = publicationsOf(SectionKind.PROGRAM, sectionId);
        Set<Long> correctedItemIds = correctedItemIdsOf(SectionKind.PROGRAM, sectionId);
        List<GradeGridStudentResponse> students = enrollments.stream()
                .map(enrollment -> studentRow(enrollment.getId(), enrollment.getStudent().getPerson(), enrollment.getStatus(),
                        enrollment.getFinalScore(), itemsByEnrollment, freeEnrollmentIds, publications, correctedItemIds))
                .sorted(byName())
                .toList();
        return response(SectionKind.PROGRAM, sectionId, section.getEvaluationScheme(), config.orElse(null), students, publications);
    }

    private TeacherGradeGridResponse courseGrid(Long sectionId) {
        CourseSubjectSection section = sectionFinder.findCourseSectionEntity(sectionId);
        List<CourseSubjectSectionEnrollment> enrollments = sectionEnrollmentFinder.findMembersWithStudentInCourseSection(sectionId);
        List<Long> enrollmentIds = enrollments.stream().map(CourseSubjectSectionEnrollment::getId).toList();
        Map<Long, List<GradeItem>> itemsByEnrollment = groupByEnrollment(
                enrollmentIds.isEmpty() ? List.of() : gradeItemRepository.findByCourseSubjectSectionEnrollmentIdIn(enrollmentIds),
                item -> item.getCourseSubjectSectionEnrollment().getId());
        Set<Long> freeEnrollmentIds = sectionFreeByAbsencesFinder
                .findCourseEnrollmentIdsFreeByAbsences(sectionId, section.getMaxAbsenceRate());
        Map<String, LocalDateTime> publications = publicationsOf(SectionKind.COURSE, sectionId);
        Set<Long> correctedItemIds = correctedItemIdsOf(SectionKind.COURSE, sectionId);
        List<GradeGridStudentResponse> students = enrollments.stream()
                .map(enrollment -> studentRow(enrollment.getId(), enrollment.getStudent().getPerson(), enrollment.getStatus(),
                        enrollment.getFinalScore(), itemsByEnrollment, freeEnrollmentIds, publications, correctedItemIds))
                .sorted(byName())
                .toList();
        return response(SectionKind.COURSE, sectionId, section.getEvaluationScheme(), null, students, publications);
    }

    private Map<String, LocalDateTime> publicationsOf(SectionKind kind, Long sectionId) {
        Map<String, LocalDateTime> publications = new HashMap<>();
        publicationRepository.findBySectionKindAndSectionId(kind, sectionId).forEach(publication ->
                publications.put(slotKey(publication.getGradeItemType(), publication.getSequenceNumber()),
                        publication.getPublishedAt()));
        return publications;
    }

    private Set<Long> correctedItemIdsOf(SectionKind kind, Long sectionId) {
        return auditRepository.findBySectionKindAndSectionIdAndKind(kind, sectionId, GradeItemAuditKind.CORRECTION).stream()
                .map(GradeItemAudit::getGradeItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String slotKey(GradeItemType type, int sequenceNumber) {
        return type + ":" + sequenceNumber;
    }

    private Map<Long, List<GradeItem>> groupByEnrollment(List<GradeItem> items, Function<GradeItem, Long> enrollmentIdOf) {
        Map<Long, List<GradeItem>> byEnrollment = new HashMap<>();
        items.forEach(item -> byEnrollment.computeIfAbsent(enrollmentIdOf.apply(item), key -> new ArrayList<>()).add(item));
        return byEnrollment;
    }

    private GradeGridStudentResponse studentRow(Long enrollmentId, Person person, EnrollmentStatus status, Grade finalScore,
                                                Map<Long, List<GradeItem>> itemsByEnrollment, Set<Long> freeEnrollmentIds,
                                                Map<String, LocalDateTime> publications, Set<Long> correctedItemIds) {
        List<GradeItem> items = itemsByEnrollment.getOrDefault(enrollmentId, List.of());
        boolean hasLoadedFinal = items.stream().anyMatch(item -> item.getType() == GradeItemType.FINAL);
        List<GradeGridCellResponse> cells = items.stream()
                .filter(item -> item.getType() != null && item.getSequenceNumber() != null)
                .sorted(Comparator.comparing(GradeItem::getType).thenComparing(GradeItem::getSequenceNumber))
                .map(item -> new GradeGridCellResponse(item.getId(), item.getType(), item.getSequenceNumber(),
                        item.getScore(), item.getDate(), item.getComments(),
                        item.getType() == GradeItemType.FINAL
                                || publications.containsKey(slotKey(item.getType(), item.getSequenceNumber())),
                        correctedItemIds.contains(item.getId())))
                .toList();
        return new GradeGridStudentResponse(
                enrollmentId, person.getLastname(), person.getName(), person.getDni(),
                GradeGridCondition.of(status, freeEnrollmentIds.contains(enrollmentId)),
                finalScore,
                status == EnrollmentStatus.REGULAR || hasLoadedFinal,
                cells);
    }

    private Comparator<GradeGridStudentResponse> byName() {
        return Comparator.comparing((GradeGridStudentResponse student) -> student.lastname().toLowerCase())
                .thenComparing(student -> student.name().toLowerCase());
    }

    private TeacherGradeGridResponse response(SectionKind kind, Long sectionId, EvaluationScheme scheme,
                                              SectionEvaluationConfig config, List<GradeGridStudentResponse> students,
                                              Map<String, LocalDateTime> publications) {
        LocalDate correctionDeadline = sectionTermEndFinder.termEndDate(kind, sectionId);
        if (scheme == null) {
            return new TeacherGradeGridResponse(sectionId, null, null, null, null, List.of(), students, correctionDeadline);
        }
        return new TeacherGradeGridResponse(
                sectionId, scheme.getName(),
                scheme.getMinRegularGrade(),
                scheme.getMinApprovalGrade(),
                scheme.getPromotionMinGrade(),
                columns(scheme, config, publications), students, correctionDeadline);
    }

    private List<GradeGridColumnResponse> columns(EvaluationScheme scheme, SectionEvaluationConfig config,
                                                  Map<String, LocalDateTime> publications) {
        List<GradeGridColumnResponse> columns = new ArrayList<>();
        int exams = scheme.getExamCountExpected() == null ? 0 : scheme.getExamCountExpected();
        int tps = scheme.getTpCountExpected() == null ? 0 : scheme.getTpCountExpected();
        for (int sequence = 1; sequence <= exams; sequence++) {
            columns.add(column(GradeItemType.EXAM, sequence, examDate(config, sequence), publications));
        }
        for (int sequence = 1; sequence <= tps; sequence++) {
            columns.add(column(GradeItemType.TP, sequence, tpDate(config, sequence), publications));
        }
        if (Boolean.TRUE.equals(scheme.getHasFinal())) {
            columns.add(new GradeGridColumnResponse(
                    GradeItemType.FINAL, 1, config == null ? null : config.getFinalCall1Date(), true, null));
        }
        return columns;
    }

    private GradeGridColumnResponse column(GradeItemType type, int sequence, LocalDate date,
                                           Map<String, LocalDateTime> publications) {
        LocalDateTime publishedAt = publications.get(slotKey(type, sequence));
        return new GradeGridColumnResponse(type, sequence, date, publishedAt != null, publishedAt);
    }

    private LocalDate examDate(SectionEvaluationConfig config, int sequence) {
        if (config == null) {
            return null;
        }
        return switch (sequence) {
            case 1 -> config.getExam1Date();
            case 2 -> config.getExam2Date();
            case 3 -> config.getExam3Date();
            case 4 -> config.getExam4Date();
            default -> null;
        };
    }

    private LocalDate tpDate(SectionEvaluationConfig config, int sequence) {
        if (config == null) {
            return null;
        }
        return switch (sequence) {
            case 1 -> config.getTp1DueDate();
            case 2 -> config.getTp2DueDate();
            case 3 -> config.getTp3DueDate();
            default -> null;
        };
    }
}
