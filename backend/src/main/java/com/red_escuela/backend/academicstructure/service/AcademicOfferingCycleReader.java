package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.AcademicOfferingCycleResponse;
import com.red_escuela.backend.academicstructure.dto.CycleSectionResponse;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.SectionModificationProposal;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SectionModificationProposalRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AcademicOfferingCycleReader {

    private final AcademicOfferingCycleRepository cycleRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final CourseSubjectSectionRepository courseSectionRepository;
    private final SectionScheduleSlotRepository slotRepository;
    private final SectionModificationProposalRepository proposalRepository;

    public AcademicOfferingCycleReader(
            AcademicOfferingCycleRepository cycleRepository,
            SubjectSectionRepository subjectSectionRepository,
            CourseSubjectSectionRepository courseSectionRepository,
            SectionScheduleSlotRepository slotRepository,
            SectionModificationProposalRepository proposalRepository
    ) {
        this.cycleRepository = cycleRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.courseSectionRepository = courseSectionRepository;
        this.slotRepository = slotRepository;
        this.proposalRepository = proposalRepository;
    }

    @Transactional(readOnly = true)
    public AcademicOfferingCycleResponse readCycle(Long cycleId) {
        return cycleRepository.findById(cycleId)
                .map(AcademicOfferingCycleResponse::from)
                .orElseThrow(() -> new CycleNotFoundException(cycleId));
    }

    @Transactional(readOnly = true)
    public List<AcademicOfferingCycleResponse> listCycles(Long programId, Long courseId, AcademicOfferingCycleStatus status) {
        if (programId != null) {
            return cycleRepository.findByProgramIdOrderByCreatedAtDesc(programId)
                    .stream().map(AcademicOfferingCycleResponse::from).toList();
        }
        if (courseId != null) {
            return cycleRepository.findByCourseIdOrderByCreatedAtDesc(courseId)
                    .stream().map(AcademicOfferingCycleResponse::from).toList();
        }
        if (status != null) {
            return cycleRepository.findByStatus(status)
                    .stream().map(AcademicOfferingCycleResponse::from).toList();
        }
        return cycleRepository.findAll()
                .stream().map(AcademicOfferingCycleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CycleSectionResponse> listCycleSections(Long cycleId) {
        var cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new CycleNotFoundException(cycleId));
        if (cycle.getProgram() != null) {
            var sections = subjectSectionRepository.findByAcademicOfferingCycleId(cycleId);
            var ids = sections.stream().map(s -> s.getId()).toList();
            var slotsBySection = ids.isEmpty() ? Map.<Long, List<SectionScheduleSlot>>of()
                    : slotRepository.findBySubjectSectionIdInOrderByIdAsc(ids).stream()
                            .collect(Collectors.groupingBy(sl -> sl.getSubjectSection().getId()));
            var proposalBySection = ids.isEmpty() ? Map.<Long, SectionModificationProposal>of()
                    : proposalRepository.findLatestBySubjectSectionIds(ids).stream()
                            .collect(Collectors.toMap(p -> p.getSubjectSection().getId(), p -> p));
            return sections.stream()
                    .map(s -> CycleSectionResponse.fromSubjectSection(
                            s,
                            slotsBySection.getOrDefault(s.getId(), List.of()),
                            proposalBySection.get(s.getId())))
                    .toList();
        }
        var sections = courseSectionRepository.findByAcademicOfferingCycleId(cycleId);
        var ids = sections.stream().map(s -> s.getId()).toList();
        var slotsBySection = ids.isEmpty() ? Map.<Long, List<SectionScheduleSlot>>of()
                : slotRepository.findByCourseSectionIdInOrderByIdAsc(ids).stream()
                        .collect(Collectors.groupingBy(sl -> sl.getCourseSection().getId()));
        return sections.stream()
                .map(s -> CycleSectionResponse.fromCourseSection(s, slotsBySection.getOrDefault(s.getId(), List.of())))
                .toList();
    }
}
