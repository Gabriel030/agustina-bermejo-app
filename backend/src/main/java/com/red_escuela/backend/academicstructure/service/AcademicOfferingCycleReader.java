package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.AcademicOfferingCycleResponse;
import com.red_escuela.backend.academicstructure.dto.CycleSectionResponse;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SectionModificationProposalRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
            return subjectSectionRepository.findByAcademicOfferingCycleId(cycleId).stream()
                    .map(s -> CycleSectionResponse.fromSubjectSection(
                            s,
                            slotRepository.findBySubjectSectionId(s.getId()),
                            proposalRepository.findFirstBySubjectSectionIdOrderByIdDesc(s.getId()).orElse(null)))
                    .toList();
        }
        return courseSectionRepository.findByAcademicOfferingCycleId(cycleId).stream()
                .map(s -> CycleSectionResponse.fromCourseSection(s, slotRepository.findByCourseSectionId(s.getId())))
                .toList();
    }
}
