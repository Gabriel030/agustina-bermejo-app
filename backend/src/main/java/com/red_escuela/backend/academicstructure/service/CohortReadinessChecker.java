package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.CurrentCurriculumPlanProvider;
import com.red_escuela.backend.academicstructure.api.ProgramCohortNotFoundException;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.SectionEvaluationApprovalPort;
import com.red_escuela.backend.academicstructure.domain.CohortReadinessItemCode;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.dto.CohortReadinessItemResponse;
import com.red_escuela.backend.academicstructure.dto.CohortReadinessResponse;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlanStatus;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramCohortRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.identity.api.PreceptorScopeFinder;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.CurrentBillingModePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CohortReadinessChecker {

    private static final int FIRST_CURRICULAR_YEAR = 1;

    private final ProgramCohortRepository cohortRepository;
    private final ProgramCurriculumFinder curriculumFinder;
    private final SubjectSectionRepository sectionRepository;
    private final SectionScheduleSlotRepository slotRepository;
    private final SectionEvaluationApprovalPort evaluationApprovalPort;
    private final PreceptorScopeFinder preceptorScopeFinder;
    private final HolidayRepository holidayRepository;
    private final TermRepository termRepository;
    private final CurrentBillingModePort billingMode;

    public CohortReadinessChecker(ProgramCohortRepository cohortRepository, ProgramCurriculumFinder curriculumFinder,
                                  SubjectSectionRepository sectionRepository, SectionScheduleSlotRepository slotRepository,
                                  SectionEvaluationApprovalPort evaluationApprovalPort, PreceptorScopeFinder preceptorScopeFinder,
                                  HolidayRepository holidayRepository, TermRepository termRepository, CurrentBillingModePort billingMode) {
        this.cohortRepository = cohortRepository;
        this.curriculumFinder = curriculumFinder;
        this.sectionRepository = sectionRepository;
        this.slotRepository = slotRepository;
        this.evaluationApprovalPort = evaluationApprovalPort;
        this.preceptorScopeFinder = preceptorScopeFinder;
        this.holidayRepository = holidayRepository;
        this.termRepository = termRepository;
        this.billingMode = billingMode;
    }

    public CohortReadinessResponse check(Long cohortId) {
        ProgramCohort cohort = cohortRepository.findById(cohortId).orElseThrow(() -> new ProgramCohortNotFoundException(cohortId));
        List<ProgramSubjectView> firstYearSubjects = curriculumFinder.findProgramSubjectsOfPlan(cohort.getPlan().getId()).stream()
                .filter(subject -> subject.curricularYear() != null && subject.curricularYear() == FIRST_CURRICULAR_YEAR)
                .toList();
        Map<Long, List<SubjectSection>> approvedSectionsBySubject = approvedSectionsBySubject(cohort, firstYearSubjects);
        List<SubjectSection> approvedSections = approvedSectionsBySubject.values().stream().flatMap(List::stream).toList();
        Set<Long> sectionIds = approvedSections.stream().map(SubjectSection::getId).collect(Collectors.toUnmodifiableSet());

        List<CohortReadinessItemResponse> items = new ArrayList<>();
        items.add(currentPlanItem(cohort));
        items.add(firstYearSectionsItem(firstYearSubjects, approvedSectionsBySubject, sectionIds));
        items.add(sectionPreceptorsItem(approvedSections, sectionIds));
        items.add(cohortResponsibleItem(cohort));
        items.add(holidaysItem(cohort));
        if (billingMode.currentMode() == BillingMode.FULL) {
            items.add(billingTemplateItem(cohort));
        }
        boolean ready = items.stream().noneMatch(item -> item.blocking() && !item.fulfilled());
        return new CohortReadinessResponse(cohort.getId(), cohort.isOpen(), cohort.getOpenedAt(), ready, List.copyOf(items));
    }

    private Map<Long, List<SubjectSection>> approvedSectionsBySubject(ProgramCohort cohort, List<ProgramSubjectView> subjects) {
        Set<Long> subjectIds = subjects.stream().map(ProgramSubjectView::subjectId).collect(Collectors.toUnmodifiableSet());
        return sectionRepository.findByTermId(cohort.getTermId()).stream()
                .filter(section -> subjectIds.contains(section.getSubject().getId()))
                .filter(section -> section.getStatus() == StatusType.ACTIVE && section.isOfferingApproved())
                .collect(Collectors.groupingBy(section -> section.getSubject().getId()));
    }

    private CohortReadinessItemResponse currentPlanItem(ProgramCohort cohort) {
        boolean fulfilled = cohort.getPlan().getStatus() == CurriculumPlanStatus.ACTIVE;
        return item(CohortReadinessItemCode.CURRENT_PLAN, fulfilled, fulfilled ? List.of() : List.of("El plan de estudios de la cohorte no está vigente"));
    }

    private CohortReadinessItemResponse firstYearSectionsItem(List<ProgramSubjectView> subjects, Map<Long, List<SubjectSection>> sectionsBySubject,
                                                             Set<Long> sectionIds) {
        Set<Long> withSlots = slotRepository.findBySubjectSectionIdInOrderByIdAsc(sectionIds).stream()
                .map(slot -> slot.getSubjectSection().getId()).collect(Collectors.toUnmodifiableSet());
        Set<Long> withEvaluation = evaluationApprovalPort.programSectionIdsWithApprovedEvaluation(sectionIds);
        List<String> reasons = new ArrayList<>();
        for (ProgramSubjectView subject : subjects) {
            List<SubjectSection> sections = sectionsBySubject.getOrDefault(subject.subjectId(), List.of());
            if (sections.isEmpty()) {
                reasons.add(subject.subjectName() + ": sin comisión aprobada en el período");
            }
            for (SubjectSection section : sections) {
                List<String> gaps = new ArrayList<>();
                if (section.getInstructors().stream().noneMatch(instructor -> instructor.getRole() == InstructorRole.LEAD)) {
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
                    reasons.add(subject.subjectName() + " (" + section.getName() + "): falta " + String.join(", ", gaps));
                }
            }
        }
        return item(CohortReadinessItemCode.FIRST_YEAR_SECTIONS, reasons.isEmpty(), reasons);
    }

    private CohortReadinessItemResponse sectionPreceptorsItem(List<SubjectSection> sections, Set<Long> sectionIds) {
        Set<Long> withPreceptor = preceptorScopeFinder.subjectSectionIdsWithPreceptor(sectionIds);
        List<String> reasons = sections.stream()
                .filter(section -> !withPreceptor.contains(section.getId()))
                .map(section -> section.getSubject().getName() + " (" + section.getName() + "): sin preceptor")
                .toList();
        return item(CohortReadinessItemCode.SECTION_PRECEPTORS, reasons.isEmpty(), reasons);
    }

    private CohortReadinessItemResponse cohortResponsibleItem(ProgramCohort cohort) {
        boolean fulfilled = !preceptorScopeFinder.responsiblePersonIdsOfCohort(cohort.getId()).isEmpty();
        return item(CohortReadinessItemCode.COHORT_RESPONSIBLE, fulfilled, fulfilled ? List.of() : List.of("La cohorte no tiene preceptor responsable"));
    }

    private CohortReadinessItemResponse holidaysItem(ProgramCohort cohort) {
        int year = termRepository.findById(cohort.getTermId()).map(term -> term.getYear()).orElse(cohort.getCursadaStart().getYear());
        boolean fulfilled = holidayRepository.existsByDateBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        return item(CohortReadinessItemCode.HOLIDAYS_LOADED, fulfilled, fulfilled ? List.of() : List.of("No hay feriados cargados para el año " + year));
    }

    private CohortReadinessItemResponse billingTemplateItem(ProgramCohort cohort) {
        boolean fulfilled = cohort.getTemplateId() != null;
        return item(CohortReadinessItemCode.BILLING_TEMPLATE, fulfilled, fulfilled ? List.of() : List.of("La cohorte no tiene plantilla de facturación"));
    }

    private CohortReadinessItemResponse item(CohortReadinessItemCode code, boolean fulfilled, List<String> reasons) {
        return new CohortReadinessItemResponse(code, code.blocking(), fulfilled, reasons);
    }
}
