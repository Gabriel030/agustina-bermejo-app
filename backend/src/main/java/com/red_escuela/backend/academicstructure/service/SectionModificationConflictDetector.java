package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.SectionModificationConflictType;
import com.red_escuela.backend.academicstructure.dto.ScheduleSlotRequest;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.exception.ClassroomNotFoundException;
import com.red_escuela.backend.academicstructure.exception.InstructorScheduleConflictException;
import com.red_escuela.backend.academicstructure.exception.SectionCapacityExceedsClassroomException;
import com.red_escuela.backend.academicstructure.exception.SectionScheduleConflictException;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Component
public class SectionModificationConflictDetector {

    private static final String NO_LABEL = "";

    private final ClassroomRepository classroomRepository;
    private final SectionConflictChecker conflictChecker;
    private final SectionShiftSlotGuard shiftSlotGuard;

    public SectionModificationConflictDetector(ClassroomRepository classroomRepository,
                                               SectionConflictChecker conflictChecker,
                                               SectionShiftSlotGuard shiftSlotGuard) {
        this.classroomRepository = classroomRepository;
        this.conflictChecker = conflictChecker;
        this.shiftSlotGuard = shiftSlotGuard;
    }

    public List<SectionModificationConflict> detectAll(SubjectSection section, List<ScheduleSlotRequest> slots, Set<Long> currentSlotIds) {
        var conflicts = new ArrayList<SectionModificationConflict>();
        for (int index = 0; index < slots.size(); index++) {
            conflicts.addAll(detectForSlot(section, slots, index, currentSlotIds));
        }
        return conflicts;
    }

    public List<SectionModificationConflict> detectForSlot(SubjectSection section, List<ScheduleSlotRequest> slots, int index, Set<Long> currentSlotIds) {
        var slot = slots.get(index);
        var conflicts = new ArrayList<SectionModificationConflict>();
        shiftSlotGuard.findViolation(section.getShift(), slot.startTime(), slot.endTime())
                .ifPresent(violation -> conflicts.add(conflict(index, SectionModificationConflictType.SLOT_OUTSIDE_SHIFT, NO_LABEL, () -> violation)));
        Classroom classroom = classroomRepository.findById(slot.classroomId()).orElse(null);
        if (classroom == null) {
            if (conflicts.isEmpty()) {
                throw new ClassroomNotFoundException(slot.classroomId());
            }
            return conflicts;
        }
        if (!classroom.canHost(section.getCapacity())) {
            conflicts.add(conflict(index, SectionModificationConflictType.CLASSROOM_CAPACITY_EXCEEDED, NO_LABEL,
                    () -> new SectionCapacityExceedsClassroomException(classroom.getId(), classroom.getCapacity(), section.getCapacity())));
        }
        conflictChecker.findClassroomOccupyingSlotsIgnoring(
                        section.getTerm(), slot.classroomId(), slot.dayOfWeek(), slot.startTime(), slot.endTime(), currentSlotIds)
                .stream().findFirst()
                .ifPresent(occupying -> conflicts.add(conflict(index, SectionModificationConflictType.CLASSROOM_OCCUPIED, label(occupying),
                        () -> new SectionScheduleConflictException(slot.classroomId(), slot.dayOfWeek(), slot.startTime(), slot.endTime()))));
        conflictChecker.findSectionInstructorsOccupations(
                        section.getTerm(), section.getId(), slot.dayOfWeek(), slot.startTime(), slot.endTime(), currentSlotIds)
                .stream().findFirst()
                .ifPresent(busy -> conflicts.add(conflict(index, SectionModificationConflictType.INSTRUCTOR_BUSY, label(busy.slot()),
                        () -> new InstructorScheduleConflictException(busy.instructorId(), slot.dayOfWeek(), slot.startTime(), slot.endTime()))));
        if (overlapsEarlierSlot(slots, index)) {
            conflicts.add(conflict(index, SectionModificationConflictType.SLOTS_OVERLAP, NO_LABEL,
                    () -> new SectionScheduleConflictException(slot.classroomId(), slot.dayOfWeek(), slot.startTime(), slot.endTime())));
        }
        return conflicts;
    }

    private boolean overlapsEarlierSlot(List<ScheduleSlotRequest> slots, int index) {
        var candidate = slots.get(index);
        return slots.subList(0, index).stream().anyMatch(other ->
                other.classroomId().equals(candidate.classroomId())
                        && other.dayOfWeek() == candidate.dayOfWeek()
                        && other.startTime().isBefore(candidate.endTime())
                        && other.endTime().isAfter(candidate.startTime()));
    }

    private SectionModificationConflict conflict(int index, SectionModificationConflictType type, String label,
                                                 Supplier<? extends RuntimeException> exception) {
        return new SectionModificationConflict(index, type, label, exception);
    }

    private String label(SectionScheduleSlot slot) {
        if (slot.getSubjectSection() != null) {
            return slot.getSubjectSection().getSubject().getName() + " · " + slot.getSubjectSection().getName();
        }
        return slot.getCourseSection().getCourseSubject().getName() + " · " + slot.getCourseSection().getName();
    }
}
