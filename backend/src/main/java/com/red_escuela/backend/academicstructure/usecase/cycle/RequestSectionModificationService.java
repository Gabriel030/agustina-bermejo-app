package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.ModifySectionRequest;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.exception.ClassroomNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.academicstructure.service.SectionConflictChecker;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class RequestSectionModificationService implements RequestSectionModificationUseCase {

    private final SubjectSectionRepository sectionRepository;
    private final SectionScheduleSlotRepository slotRepository;
    private final ClassroomRepository classroomRepository;
    private final SectionConflictChecker conflictChecker;

    public RequestSectionModificationService(
            SubjectSectionRepository sectionRepository,
            SectionScheduleSlotRepository slotRepository,
            ClassroomRepository classroomRepository,
            SectionConflictChecker conflictChecker
    ) {
        this.sectionRepository = sectionRepository;
        this.slotRepository = slotRepository;
        this.classroomRepository = classroomRepository;
        this.conflictChecker = conflictChecker;
    }

    @Override
    @Transactional
    public void requestModification(Long sectionId, ModifySectionRequest request) {
        var section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId));

        if (request.slots() != null) {
            var existing = slotRepository.findBySubjectSectionId(sectionId);
            slotRepository.deleteAll(existing);

            for (var slotReq : request.slots()) {
                var classroom = classroomRepository.findById(slotReq.classroomId())
                        .orElseThrow(() -> new ClassroomNotFoundException(slotReq.classroomId()));
                classroom.ensureCanHost(section.getCapacity());
                conflictChecker.checkClassroomAvailability(section.getTerm(), slotReq.classroomId(), slotReq.dayOfWeek(), slotReq.startTime(), slotReq.endTime());
                var slot = new SectionScheduleSlot();
                slot.setSubjectSection(section);
                slot.setClassroom(classroom);
                slot.setDayOfWeek(slotReq.dayOfWeek());
                slot.setStartTime(slotReq.startTime());
                slot.setEndTime(slotReq.endTime());
                slotRepository.save(slot);
            }
        }

        section.setPendingApproval(true);
        sectionRepository.save(section);
    }
}
