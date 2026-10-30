package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class SectionScheduleFinder {

    private final SectionScheduleSlotRepository slotRepository;

    public SectionScheduleFinder(SectionScheduleSlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    public List<SectionSlotView> findProgramSectionSlots(Long programSectionId) {
        return slotRepository.findBySubjectSectionId(programSectionId).stream()
                .map(slot -> new SectionSlotView(
                        slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getClassroom().getName()))
                .toList();
    }
}
