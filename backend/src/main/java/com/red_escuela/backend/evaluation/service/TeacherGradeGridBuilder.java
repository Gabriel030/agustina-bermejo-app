package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
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
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Service
public class TeacherGradeGridBuilder {

    private final SectionFinder sectionFinder;
    private final SectionTeachingFinder sectionTeachingFinder;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final SectionFreeByAbsencesFinder sectionFreeByAbsencesFinder;
    private final GradeItemRepository gradeItemRepository;
    private final SectionEvaluationConfigRepository configRepository;

    public TeacherGradeGridBuilder(SectionFinder sectionFinder,
                                   SectionTeachingFinder sectionTeachingFinder,
                                   SectionEnrollmentFinder sectionEnrollmentFinder,
                                   SectionFreeByAbsencesFinder sectionFreeByAbsencesFinder,
                                   GradeItemRepository gradeItemRepository,
                                   SectionEvaluationConfigRepository configRepository) {
        this.sectionFinder = sectionFinder;
        this.sectionTeachingFinder = sectionTeachingFinder;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.sectionFreeByAbsencesFinder = sectionFreeByAbsencesFinder;
        this.gradeItemRepository = gradeItemRepository;
        this.configRepository = configRepository;
    }

    @Transactional(readOnly = true)
    public TeacherGradeGridResponse buildForProgramSection(Long sectionId, Long instructorId) {
        sectionFinder.requireExists(SectionKind.PROGRAM, sectionId);
        sectionTeachingFinder.requireTeaches(SectionKind.PROGRAM, sectionId, instructorId);
        SubjectSection section = sectionFinder.findProgramSectionEntity(sectionId);
        List<SubjectSectionEnrollment> enrollments = sectionEnrollmentFinder.findMembersWithStudentInProgramSection(sectionId);
        List<Long> enrollmentIds = enrollments.stream().map(SubjectSectionEnrollment::getId).toList();
        Map<Long, List<GradeItem>> itemsByEnrollment = groupByEnrollment(
                enrollmentIds.isEmpty() ? List.of() : gradeItemRepository.findByProgramSubjectSectionEnrollmentIdIn(enrollmentIds),
                item -> item.getProgramSubjectSectionEnrollment().getId());
        Set<Long> freeEnrollmentIds = sectionFreeByAbsencesFinder
                .findProgramEnrollmentIdsFreeByAbsences(sectionId, section.getMaxAbsenceRate());
        Optional<SectionEvaluationConfig> config = configRepository.findBySubjectSectionId(sectionId);
        List<GradeGridStudentResponse> students = enrollments.stream()
                .map(enrollment -> studentRow(enrollment.getId(), enrollment.getStudent().getPerson(), enrollment.getStatus(),
                        enrollment.getFinalScore(), itemsByEnrollment, freeEnrollmentIds))
                .sorted(byName())
                .toList();
        return response(sectionId, section.getEvaluationScheme(), config.orElse(null), students);
    }

    @Transactional(readOnly = true)
    public TeacherGradeGridResponse buildForCourseSection(Long sectionId, Long instructorId) {
        sectionFinder.requireExists(SectionKind.COURSE, sectionId);
        sectionTeachingFinder.requireTeaches(SectionKind.COURSE, sectionId, instructorId);
        CourseSubjectSection section = sectionFinder.findCourseSectionEntity(sectionId);
        List<CourseSubjectSectionEnrollment> enrollments = sectionEnrollmentFinder.findMembersWithStudentInCourseSection(sectionId);
        List<Long> enrollmentIds = enrollments.stream().map(CourseSubjectSectionEnrollment::getId).toList();
        Map<Long, List<GradeItem>> itemsByEnrollment = groupByEnrollment(
                enrollmentIds.isEmpty() ? List.of() : gradeItemRepository.findByCourseSubjectSectionEnrollmentIdIn(enrollmentIds),
                item -> item.getCourseSubjectSectionEnrollment().getId());
        Set<Long> freeEnrollmentIds = sectionFreeByAbsencesFinder
                .findCourseEnrollmentIdsFreeByAbsences(sectionId, section.getMaxAbsenceRate());
        List<GradeGridStudentResponse> students = enrollments.stream()
                .map(enrollment -> studentRow(enrollment.getId(), enrollment.getStudent().getPerson(), enrollment.getStatus(),
                        enrollment.getFinalScore(), itemsByEnrollment, freeEnrollmentIds))
                .sorted(byName())
                .toList();
        return response(sectionId, section.getEvaluationScheme(), null, students);
    }

    private Map<Long, List<GradeItem>> groupByEnrollment(List<GradeItem> items, Function<GradeItem, Long> enrollmentIdOf) {
        Map<Long, List<GradeItem>> byEnrollment = new HashMap<>();
        items.forEach(item -> byEnrollment.computeIfAbsent(enrollmentIdOf.apply(item), key -> new ArrayList<>()).add(item));
        return byEnrollment;
    }

    private GradeGridStudentResponse studentRow(Long enrollmentId, Person person, EnrollmentStatus status, Grade finalScore,
                                                Map<Long, List<GradeItem>> itemsByEnrollment, Set<Long> freeEnrollmentIds) {
        List<GradeItem> items = itemsByEnrollment.getOrDefault(enrollmentId, List.of());
        boolean hasLoadedFinal = items.stream().anyMatch(item -> item.getType() == GradeItemType.FINAL);
        List<GradeGridCellResponse> cells = items.stream()
                .filter(item -> item.getType() != null && item.getSequenceNumber() != null)
                .sorted(Comparator.comparing(GradeItem::getType).thenComparing(GradeItem::getSequenceNumber))
                .map(item -> new GradeGridCellResponse(item.getId(), item.getType(), item.getSequenceNumber(),
                        item.getScore(), item.getDate(), item.getComments()))
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

    private TeacherGradeGridResponse response(Long sectionId, EvaluationScheme scheme, SectionEvaluationConfig config,
                                              List<GradeGridStudentResponse> students) {
        if (scheme == null) {
            return new TeacherGradeGridResponse(sectionId, null, null, null, null, List.of(), students);
        }
        return new TeacherGradeGridResponse(
                sectionId, scheme.getName(),
                scheme.getMinRegularGrade(),
                scheme.getMinApprovalGrade(),
                scheme.getPromotionMinGrade(),
                columns(scheme, config), students);
    }

    private List<GradeGridColumnResponse> columns(EvaluationScheme scheme, SectionEvaluationConfig config) {
        List<GradeGridColumnResponse> columns = new ArrayList<>();
        int exams = scheme.getExamCountExpected() == null ? 0 : scheme.getExamCountExpected();
        int tps = scheme.getTpCountExpected() == null ? 0 : scheme.getTpCountExpected();
        for (int sequence = 1; sequence <= exams; sequence++) {
            columns.add(new GradeGridColumnResponse(GradeItemType.EXAM, sequence, examDate(config, sequence)));
        }
        for (int sequence = 1; sequence <= tps; sequence++) {
            columns.add(new GradeGridColumnResponse(GradeItemType.TP, sequence, tpDate(config, sequence)));
        }
        if (Boolean.TRUE.equals(scheme.getHasFinal())) {
            columns.add(new GradeGridColumnResponse(GradeItemType.FINAL, 1, config == null ? null : config.getFinalCall1Date()));
        }
        return columns;
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
