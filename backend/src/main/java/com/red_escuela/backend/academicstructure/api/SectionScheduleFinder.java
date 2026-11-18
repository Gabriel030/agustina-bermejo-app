package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public List<SectionSlotView> findCourseSectionSlots(Long courseSectionId) {
        return slotRepository.findByCourseSectionId(courseSectionId).stream()
                .map(slot -> new SectionSlotView(
                        slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getClassroom().getName()))
                .toList();
    }

    public Map<Long, List<SectionSlotView>> findProgramSectionSlots(Collection<Long> programSectionIds) {
        if (programSectionIds.isEmpty()) {
            return Map.of();
        }
        return slotRepository.findWithClassroomBySubjectSectionIdIn(programSectionIds).stream()
                .sorted(Comparator.comparing(slot -> slot.getId()))
                .collect(Collectors.groupingBy(slot -> slot.getSubjectSection().getId(),
                        Collectors.mapping(slot -> new SectionSlotView(
                                slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getClassroom().getName()), Collectors.toList())));
    }

    public Map<Long, List<SectionSlotView>> findCourseSectionSlots(Collection<Long> courseSectionIds) {
        if (courseSectionIds.isEmpty()) {
            return Map.of();
        }
        return slotRepository.findWithClassroomByCourseSectionIdIn(courseSectionIds).stream()
                .sorted(Comparator.comparing(slot -> slot.getId()))
                .collect(Collectors.groupingBy(slot -> slot.getCourseSection().getId(),
                        Collectors.mapping(slot -> new SectionSlotView(
                                slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getClassroom().getName()), Collectors.toList())));
    }
}
