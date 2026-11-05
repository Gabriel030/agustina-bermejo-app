package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.exception.ClassroomInUseException;
import com.red_escuela.backend.academicstructure.exception.ClassroomNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.academicstructure.repository.SectionModificationProposalRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.TreeSet;

@Component
public class ClassroomDeactivator {

    private final ClassroomRepository classroomRepository;
    private final SectionScheduleSlotRepository slotRepository;
    private final SectionModificationProposalRepository proposalRepository;

    public ClassroomDeactivator(
            ClassroomRepository classroomRepository,
            SectionScheduleSlotRepository slotRepository,
            SectionModificationProposalRepository proposalRepository
    ) {
        this.classroomRepository = classroomRepository;
        this.slotRepository = slotRepository;
        this.proposalRepository = proposalRepository;
    }

    @Transactional
    public void deactivateClassroom(Long classroomId) {
        var classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ClassroomNotFoundException(classroomId));

        var affectedSections = new TreeSet<String>();
        slotRepository.findSlotsOfActiveSectionsInClassroom(classroomId)
                .forEach(slot -> affectedSections.add(sectionNameOf(slot)));
        affectedSections.addAll(proposalRepository.findSectionNamesWithPendingProposalIn(classroomId));
        if (!affectedSections.isEmpty()) {
            throw new ClassroomInUseException(classroomId, List.copyOf(affectedSections));
        }

        classroom.setStatus(StatusType.INACTIVE);
        classroomRepository.save(classroom);
    }

    private String sectionNameOf(SectionScheduleSlot slot) {
        return slot.getSubjectSection() != null ? slot.getSubjectSection().getName() : slot.getCourseSection().getName();
    }
}
