package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.ClassroomSlotOccupancyResponse;
import com.red_escuela.backend.academicstructure.dto.ClassroomSlotOccupancyResponse.OccupyingSection;
import com.red_escuela.backend.academicstructure.dto.ClassroomSlotOccupancyResponse.OccupyingTerm;
import com.red_escuela.backend.academicstructure.dto.ClassroomSlotOccupancyResponse.SectionKind;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import org.springframework.stereotype.Component;

@Component
public class ClassroomSlotOccupancyAssembler {

    public ClassroomSlotOccupancyResponse assemble(SectionScheduleSlot slot, Term referenceTerm) {
        SubjectSection subjectSection = slot.getSubjectSection();
        CourseSubjectSection courseSection = slot.getCourseSection();
        Term slotTerm = subjectSection != null ? subjectSection.getTerm() : courseSection.getTerm();
        OccupyingSection section = subjectSection != null
                ? new OccupyingSection(subjectSection.getId(), subjectSection.getCode(), subjectSection.getSubject().getName(), subjectSection.getName(), SectionKind.PROGRAM)
                : new OccupyingSection(courseSection.getId(), courseSection.getCode(), courseSection.getCourseSubject().getCourse().getName(), courseSection.getName(), SectionKind.COURSE);
        OccupyingTerm term = new OccupyingTerm(slotTerm.getId(), slotTerm.getDescription(), slotTerm.getStartDate(), slotTerm.getEndDate());
        return new ClassroomSlotOccupancyResponse(
                slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), section, term, overlapsInDates(slotTerm, referenceTerm));
    }

    private boolean overlapsInDates(Term slotTerm, Term referenceTerm) {
        return slotTerm.getId().equals(referenceTerm.getId())
                || (!slotTerm.getStartDate().isAfter(referenceTerm.getEndDate()) && !slotTerm.getEndDate().isBefore(referenceTerm.getStartDate()));
    }
}
