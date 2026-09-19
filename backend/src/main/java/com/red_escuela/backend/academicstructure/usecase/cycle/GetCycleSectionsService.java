package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.CycleSectionResponse;
import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
@Transactional(readOnly = true)
public class GetCycleSectionsService implements GetCycleSectionsUseCase {

    private final AcademicOfferingCycleRepository cycleRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final CourseSubjectSectionRepository courseSectionRepository;
    private final SectionScheduleSlotRepository slotRepository;

    public GetCycleSectionsService(
            AcademicOfferingCycleRepository cycleRepository,
            SubjectSectionRepository subjectSectionRepository,
            CourseSubjectSectionRepository courseSectionRepository,
            SectionScheduleSlotRepository slotRepository
    ) {
        this.cycleRepository = cycleRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.courseSectionRepository = courseSectionRepository;
        this.slotRepository = slotRepository;
    }

    @Override
    public List<CycleSectionResponse> getSections(Long cycleId) {
        var cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new CycleNotFoundException(cycleId));

        if (cycle.getProgram() != null) {
            return subjectSectionRepository.findByAcademicOfferingCycleId(cycleId).stream()
                    .map(s -> CycleSectionResponse.fromSubjectSection(s, slotRepository.findBySubjectSectionId(s.getId())))
                    .toList();
        }

        return courseSectionRepository.findByAcademicOfferingCycleId(cycleId).stream()
                .map(s -> CycleSectionResponse.fromCourseSection(s, slotRepository.findByCourseSectionId(s.getId())))
                .toList();
    }
}
