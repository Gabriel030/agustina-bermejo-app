package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import org.springframework.stereotype.Service;

@Service
public class SectionIdentityAssigner {

    private final SectionCodeGenerator codeGenerator;
    private final SectionShiftSlotGuard shiftSlotGuard;
    private final SectionScheduleSlotRepository slotRepository;

    public SectionIdentityAssigner(
            SectionCodeGenerator codeGenerator,
            SectionShiftSlotGuard shiftSlotGuard,
            SectionScheduleSlotRepository slotRepository
    ) {
        this.codeGenerator = codeGenerator;
        this.shiftSlotGuard = shiftSlotGuard;
        this.slotRepository = slotRepository;
    }

    public void identifyNewSubjectSection(SubjectSection section, SectionShift shift, String description) {
        section.setShift(shift);
        section.setDescription(description);
        section.setCode(codeGenerator.nextCodeForSubject(section.getSubject().getCode(), section.getTerm()));
    }

    public void identifyNewCourseSection(CourseSubjectSection section, SectionShift shift, String description) {
        section.setShift(shift);
        section.setDescription(description);
        section.setCode(codeGenerator.nextCodeForCourseSubject(section.getCourseSubject().getId(), section.getTerm()));
    }

    public void changeSubjectSectionShift(SubjectSection section, SectionShift shift) {
        slotRepository.findBySubjectSectionId(section.getId())
                .forEach(slot -> shiftSlotGuard.ensureInsideShift(shift, slot.getStartTime(), slot.getEndTime()));
        section.setShift(shift);
    }

    public void changeCourseSectionShift(CourseSubjectSection section, SectionShift shift) {
        slotRepository.findByCourseSectionId(section.getId())
                .forEach(slot -> shiftSlotGuard.ensureInsideShift(shift, slot.getStartTime(), slot.getEndTime()));
        section.setShift(shift);
    }
}
