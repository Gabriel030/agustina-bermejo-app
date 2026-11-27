package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.exception.InstructorScheduleConflictException;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.dto.AssignInstructorRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateSectionRequest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.exception.InstructorAlreadyAssignedException;
import com.red_escuela.backend.academicstructure.exception.InstructorNotFoundException;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.exception.SectionInstructorNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionLastInstructorRemovalException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import jakarta.persistence.EntityManager;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.red_escuela.backend.academicstructure.api.SectionEnrollmentActivityPort;
import com.red_escuela.backend.academicstructure.api.SectionEvaluationSetupPort;
import com.red_escuela.backend.academicstructure.exception.SectionEvaluationSchemeLockedException;
import com.red_escuela.backend.academicstructure.exception.SectionHasActiveEnrollmentsException;

@Service
public class SubjectSectionService {
    private final SubjectSectionRepository sectionRepository;
    private final SubjectSectionInstructorRepository sectionInstructorRepository;
    private final InstructorRepository instructorRepository;
    private final EntityManager entityManager;
    private final SectionEvaluationSetupPort evaluationSetupPort;
    private final SectionIdentityAssigner identityAssigner;
    private final SectionConflictChecker conflictChecker;
    private final SectionReactivationAvailabilityChecker reactivationChecker;
    private final SectionScheduleSlotRepository slotRepository;
    private final SectionEnrollmentActivityPort enrollmentActivityPort;
    private final CapacityChangeEvents capacityChangeEvents;

    public SubjectSectionService(
            SubjectSectionRepository sectionRepository,
            SubjectSectionInstructorRepository sectionInstructorRepository,
            InstructorRepository instructorRepository,
            EntityManager entityManager,
            SectionEvaluationSetupPort evaluationSetupPort,
            SectionIdentityAssigner identityAssigner,
            SectionConflictChecker conflictChecker,
            SectionReactivationAvailabilityChecker reactivationChecker,
            SectionScheduleSlotRepository slotRepository,
            SectionEnrollmentActivityPort enrollmentActivityPort,
            CapacityChangeEvents capacityChangeEvents) {
        this.capacityChangeEvents = capacityChangeEvents;
        this.enrollmentActivityPort = enrollmentActivityPort;
        this.sectionRepository = sectionRepository;
        this.sectionInstructorRepository = sectionInstructorRepository;
        this.instructorRepository = instructorRepository;
        this.entityManager = entityManager;
        this.evaluationSetupPort = evaluationSetupPort;
        this.identityAssigner = identityAssigner;
        this.conflictChecker = conflictChecker;
        this.reactivationChecker = reactivationChecker;
        this.slotRepository = slotRepository;
    }

    @Transactional
    public SubjectSection update(Long id, UpdateSectionRequest request) {
        SubjectSection section = findOrThrow(id);
        Integer previousCapacity = section.getCapacity();
        if (request.name() != null) {
            section.setName(request.name());
        }
        if (request.schedule() != null) {
            section.setSchedule(request.schedule());
        }
        if (request.capacity() != null) {
            section.setCapacity(request.capacity());
        }
        if (request.description() != null) {
            section.setDescription(request.description());
        }
        if (request.shift() != null) {
            identityAssigner.changeSubjectSectionShift(section, request.shift());
        }
        if (request.evaluationSchemeId() != null) {
            requireEvaluationSchemeReassignable(section, request.evaluationSchemeId());
            section.setEvaluationScheme(resolveEvaluationScheme(request.evaluationSchemeId()));
        }
        if (request.maxAbsencePercent() != null) {
            section.setMaxAbsenceRate(request.maxAbsencePercent());
        }
        SubjectSection saved = sectionRepository.save(section);
        capacityChangeEvents.programSectionChanged(saved.getId(), previousCapacity, saved.getCapacity());
        return saved;
    }

    public List<SubjectSection> listBySubject(Long subjectId, StatusType status) {
        return status == null
                ? sectionRepository.findBySubjectId(subjectId)
                : sectionRepository.findBySubjectIdAndStatus(subjectId, status);
    }

    public SubjectSection get(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public void deactivate(Long id) {
        SubjectSection section = findOrThrow(id);
        if (enrollmentActivityPort.hasLiveEnrollmentsInProgramSection(id)) {
            throw new SectionHasActiveEnrollmentsException(id);
        }
        section.setStatus(StatusType.INACTIVE);
        sectionRepository.saveAndFlush(section);
    }

    @Transactional
    public void reactivate(Long id) {
        SubjectSection section = findOrThrow(id);
        reactivationChecker.checkSubjectSectionCanBeReactivated(section);
        section.setStatus(StatusType.ACTIVE);
        sectionRepository.save(section);
    }

    @Transactional
    public SubjectSectionInstructor assignInstructor(Long sectionId, AssignInstructorRequest request) {
        SubjectSection section = findOrThrow(sectionId);
        Instructor instructor = instructorRepository.findById(request.instructorId())
                .orElseThrow(() -> new InstructorNotFoundException(request.instructorId()));
        List<SectionScheduleSlot> sectionSlots = slotRepository.findBySubjectSectionId(sectionId);
        conflictChecker.checkInstructorAvailability(section.getTerm(), instructor.getId(), sectionSlots);

        SubjectSectionInstructor assignment = new SubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(instructor);
        assignment.setRole(request.role());
        assignment.setStartAt(request.startAt());
        assignment.setEndAt(request.endAt());
        assignment.setHoursPerWeek(request.hoursPerWeek());
        assignment.setNotes(request.notes());

        try {
            return sectionInstructorRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException e) {
            if (ScheduleExclusionViolation.isInstructorOverlap(e) && !sectionSlots.isEmpty()) {
                SectionScheduleSlot slot = sectionSlots.get(0);
                throw new InstructorScheduleConflictException(
                        request.instructorId(), slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime());
            }
            if (ScheduleExclusionViolation.isInstructorOverlap(e)) {
                throw e;
            }
            throw new InstructorAlreadyAssignedException(request.instructorId(), sectionId);
        } catch (CannotAcquireLockException e) {
            if (ScheduleExclusionViolation.isDeadlock(e) && !sectionSlots.isEmpty()) {
                SectionScheduleSlot slot = sectionSlots.get(0);
                throw new InstructorScheduleConflictException(
                        request.instructorId(), slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime());
            }
            throw e;
        }
    }

    public List<SubjectSectionInstructor> listInstructors(Long sectionId) {
        return sectionInstructorRepository.findBySectionId(sectionId);
    }

    @Transactional
    public void removeInstructor(Long sectionId, Long instructorAssignmentId) {
        SubjectSection section = findOrThrow(sectionId);
        SubjectSectionInstructor assignment = sectionInstructorRepository.findById(instructorAssignmentId)
                .filter(a -> a.getSection().getId().equals(sectionId))
                .orElseThrow(() -> new SectionInstructorNotFoundException(instructorAssignmentId));
        if (section.getStatus() == StatusType.ACTIVE && isOnlyLead(assignment, sectionInstructorRepository.findBySectionId(sectionId))) {
            throw new SectionLastInstructorRemovalException(sectionId);
        }
        sectionInstructorRepository.delete(assignment);
    }

    private boolean isOnlyLead(SubjectSectionInstructor assignment, List<SubjectSectionInstructor> assignments) {
        return assignment.getRole() == InstructorRole.LEAD
                && assignments.stream().noneMatch(other -> !other.getId().equals(assignment.getId()) && other.getRole() == InstructorRole.LEAD);
    }

    private SubjectSection findOrThrow(Long id) {
        return sectionRepository.findById(id).orElseThrow(() -> new SectionNotFoundException(id));
    }

    private EvaluationScheme resolveEvaluationScheme(Long id) {
        evaluationSetupPort.requireEvaluationSchemeExists(id);
        return entityManager.find(EvaluationScheme.class, id);
    }

    private void requireEvaluationSchemeReassignable(SubjectSection section, Long newSchemeId) {
        Long currentSchemeId = section.getEvaluationScheme() == null ? null : section.getEvaluationScheme().getId();
        if (newSchemeId.equals(currentSchemeId)) {
            return;
        }
        if (evaluationSetupPort.hasGradesLoadedInProgramSection(section.getId())) {
            throw new SectionEvaluationSchemeLockedException(section.getId());
        }
    }
}
