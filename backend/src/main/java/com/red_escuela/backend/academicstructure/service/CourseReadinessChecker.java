package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.SectionEvaluationApprovalPort;
import com.red_escuela.backend.academicstructure.domain.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.domain.CourseReadinessItemCode;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.dto.CourseReadinessItemResponse;
import com.red_escuela.backend.academicstructure.dto.CourseReadinessResponse;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.identity.api.PreceptorScopeFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CourseReadinessChecker {

    private final CourseRepository courseRepository;
    private final CourseSubjectSectionRepository sectionRepository;
    private final SectionScheduleSlotRepository slotRepository;
    private final SectionEvaluationApprovalPort evaluationApprovalPort;
    private final PreceptorScopeFinder preceptorScopeFinder;

    public CourseReadinessChecker(CourseRepository courseRepository, CourseSubjectSectionRepository sectionRepository,
                                  SectionScheduleSlotRepository slotRepository, SectionEvaluationApprovalPort evaluationApprovalPort,
                                  PreceptorScopeFinder preceptorScopeFinder) {
        this.courseRepository = courseRepository;
        this.sectionRepository = sectionRepository;
        this.slotRepository = slotRepository;
        this.evaluationApprovalPort = evaluationApprovalPort;
        this.preceptorScopeFinder = preceptorScopeFinder;
    }

    public CourseReadinessResponse check(Long courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new CourseNotFoundException(courseId);
        }
        List<CourseSubjectSection> sections = sectionRepository.findByCourseSubjectCourseId(courseId);
        List<CourseReadinessItemResponse> items = List.of(
                courseResponsibleItem(courseId),
                sectionsItem(sections),
                item(CourseReadinessItemCode.SECTION_PRECEPTORS, sectionsWithoutPreceptor(sections)));
        boolean ready = items.stream().noneMatch(item -> item.blocking() && !item.fulfilled());
        boolean complete = items.stream().allMatch(CourseReadinessItemResponse::fulfilled);
        return new CourseReadinessResponse(courseId, ready, complete, items);
    }

    public List<String> sectionsWithoutPreceptor(Collection<CourseSubjectSection> sections) {
        Set<Long> sectionIds = sections.stream().map(CourseSubjectSection::getId).collect(Collectors.toUnmodifiableSet());
        Set<Long> withPreceptor = preceptorScopeFinder.courseSectionIdsWithPreceptor(sectionIds);
        return sections.stream()
                .filter(section -> !withPreceptor.contains(section.getId()))
                .map(section -> label(section) + ": sin preceptor")
                .toList();
    }

    private CourseReadinessItemResponse courseResponsibleItem(Long courseId) {
        boolean fulfilled = !preceptorScopeFinder.responsiblePersonIdsOfCourse(courseId).isEmpty();
        return new CourseReadinessItemResponse(CourseReadinessItemCode.COURSE_RESPONSIBLE, CourseReadinessItemCode.COURSE_RESPONSIBLE.blocking(),
                fulfilled, fulfilled ? List.of() : List.of("El curso no tiene preceptor responsable"));
    }

    private CourseReadinessItemResponse sectionsItem(List<CourseSubjectSection> sections) {
        if (sections.isEmpty()) {
            return item(CourseReadinessItemCode.COURSE_SECTIONS, List.of("El curso no tiene comisiones"));
        }
        Set<Long> sectionIds = sections.stream().map(CourseSubjectSection::getId).collect(Collectors.toUnmodifiableSet());
        Set<Long> withSlots = slotRepository.findByCourseSectionIdInOrderByIdAsc(sectionIds).stream()
                .map(SectionScheduleSlot::getCourseSection).map(CourseSubjectSection::getId).collect(Collectors.toUnmodifiableSet());
        Set<Long> withEvaluation = evaluationApprovalPort.courseSectionIdsWithApprovedEvaluation(sectionIds);
        List<String> reasons = new ArrayList<>();
        for (CourseSubjectSection section : sections) {
            List<String> gaps = new ArrayList<>();
            if (section.getInstructors() == null || section.getInstructors().stream().noneMatch(i -> i.getRole() == InstructorRole.LEAD)) {
                gaps.add("titular");
            }
            if (section.getCapacity() == null || section.getCapacity() <= 0) {
                gaps.add("cupo");
            }
            if (!withSlots.contains(section.getId())) {
                gaps.add("horario y aula");
            }
            if (!withEvaluation.contains(section.getId())) {
                gaps.add("evaluación aprobada");
            }
            if (!gaps.isEmpty()) {
                reasons.add(label(section) + ": falta " + String.join(", ", gaps));
            }
        }
        return item(CourseReadinessItemCode.COURSE_SECTIONS, reasons);
    }

    private static CourseReadinessItemResponse item(CourseReadinessItemCode code, List<String> reasons) {
        return new CourseReadinessItemResponse(code, code.blocking(), reasons.isEmpty(), List.copyOf(reasons));
    }

    private static String label(CourseSubjectSection section) {
        return section.getCourseSubject().getName() + " (" + section.getName() + ")";
    }
}
