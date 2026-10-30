package com.red_escuela.backend.academicstructure.adapter;

import com.red_escuela.backend.academicstructure.dto.SectionSlotView;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.usecase.SectionScheduleQueries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class RepositorySectionScheduleQueries implements SectionScheduleQueries {

    private final SectionScheduleSlotRepository slotRepository;

    public RepositorySectionScheduleQueries(SectionScheduleSlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    @Override
    public List<SectionSlotView> findProgramSectionSlots(Long programSectionId) {
        return slotRepository.findBySubjectSectionId(programSectionId).stream()
                .map(slot -> new SectionSlotView(
                        slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getClassroom().getName()))
                .toList();
    }
}
