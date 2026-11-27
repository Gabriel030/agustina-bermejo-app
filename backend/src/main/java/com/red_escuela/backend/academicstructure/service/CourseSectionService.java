package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.exception.InstructorScheduleConflictException;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.dto.AssignInstructorRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateSectionRequest;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.api.SectionEnrollmentActivityPort;
import com.red_escuela.backend.academicstructure.api.SectionEvaluationSetupPort;
import com.red_escuela.backend.academicstructure.exception.SectionEvaluationSchemeLockedException;
import com.red_escuela.backend.academicstructure.exception.SectionHasActiveEnrollmentsException;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.exception.InstructorAlreadyAssignedException;
import com.red_escuela.backend.academicstructure.exception.InstructorNotFoundException;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.exception.SectionInstructorNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionLastInstructorRemovalException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import jakarta.persistence.EntityManager;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CourseSectionService {
    private final CourseSubjectSectionRepository sectionRepository;
    private final CourseSubjectSectionInstructorRepository sectionInstructorRepository;
    private final InstructorRepository instructorRepository;
    private final EntityManager entityManager;
    private final SectionEvaluationSetupPort evaluationSetupPort;
    private final SectionIdentityAssigner identityAssigner;
    private final SectionConflictChecker conflictChecker;
    private final SectionReactivationAvailabilityChecker reactivationChecker;
    private final SectionScheduleSlotRepository slotRepository;
    private final SectionEnrollmentActivityPort enrollmentActivityPort;
    private final CapacityChangeEvents capacityChangeEvents;

    public CourseSectionService(
            CourseSubjectSectionRepository sectionRepository,
            CourseSubjectSectionInstructorRepository sectionInstructorRepository,
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
    public CourseSubjectSection update(Long id, UpdateSectionRequest request) {
        CourseSubjectSection section = findOrThrow(id);
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
            identityAssigner.changeCourseSectionShift(section, request.shift());
        }
        if (request.evaluationSchemeId() != null) {
            requireEvaluationSchemeReassignable(section, request.evaluationSchemeId());
            section.setEvaluationScheme(resolveEvaluationScheme(request.evaluationSchemeId()));
        }
        CourseSubjectSection saved = sectionRepository.save(section);
        capacityChangeEvents.courseSectionChanged(saved.getId(), previousCapacity, saved.getCapacity());
        return saved;
    }

    public List<CourseSubjectSection> listByCourseSubject(Long courseSubjectId, StatusType status) {
        return status == null
                ? sectionRepository.findByCourseSubjectId(courseSubjectId)
                : sectionRepository.findByCourseSubjectIdAndStatus(courseSubjectId, status);
    }

    public CourseSubjectSection get(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public void deactivate(Long id) {
        CourseSubjectSection section = findOrThrow(id);
        if (enrollmentActivityPort.hasLiveEnrollmentsInCourseSection(id)) {
            throw new SectionHasActiveEnrollmentsException(id);
        }
        section.setStatus(StatusType.INACTIVE);
        sectionRepository.saveAndFlush(section);
    }

    @Transactional
    public void reactivate(Long id) {
        CourseSubjectSection section = findOrThrow(id);
        reactivationChecker.checkCourseSectionCanBeReactivated(section);
        section.setStatus(StatusType.ACTIVE);
        sectionRepository.save(section);
    }

    @Transactional
    public CourseSubjectSectionInstructor assignInstructor(Long sectionId, AssignInstructorRequest request) {
        CourseSubjectSection section = findOrThrow(sectionId);
        Instructor instructor = instructorRepository.findById(request.instructorId())
                .orElseThrow(() -> new InstructorNotFoundException(request.instructorId()));
        List<SectionScheduleSlot> sectionSlots = slotRepository.findByCourseSectionId(sectionId);
        conflictChecker.checkInstructorAvailability(section.getTerm(), instructor.getId(), sectionSlots);

        CourseSubjectSectionInstructor assignment = new CourseSubjectSectionInstructor();
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

    public List<CourseSubjectSectionInstructor> listInstructors(Long sectionId) {
        return sectionInstructorRepository.findBySectionId(sectionId);
    }

    @Transactional
    public void removeInstructor(Long sectionId, Long instructorAssignmentId) {
        CourseSubjectSection section = findOrThrow(sectionId);
        CourseSubjectSectionInstructor assignment = sectionInstructorRepository.findById(instructorAssignmentId)
                .filter(a -> a.getSection().getId().equals(sectionId))
                .orElseThrow(() -> new SectionInstructorNotFoundException(instructorAssignmentId));
        if (section.getStatus() == StatusType.ACTIVE && isOnlyLead(assignment, sectionInstructorRepository.findBySectionId(sectionId))) {
            throw new SectionLastInstructorRemovalException(sectionId);
        }
        sectionInstructorRepository.delete(assignment);
    }

    private boolean isOnlyLead(CourseSubjectSectionInstructor assignment, List<CourseSubjectSectionInstructor> assignments) {
        return assignment.getRole() == InstructorRole.LEAD
                && assignments.stream().noneMatch(other -> !other.getId().equals(assignment.getId()) && other.getRole() == InstructorRole.LEAD);
    }

    private CourseSubjectSection findOrThrow(Long id) {
        return sectionRepository.findById(id).orElseThrow(() -> new SectionNotFoundException(id));
    }

    private EvaluationScheme resolveEvaluationScheme(Long id) {
        evaluationSetupPort.requireEvaluationSchemeExists(id);
        return entityManager.find(EvaluationScheme.class, id);
    }

    private void requireEvaluationSchemeReassignable(CourseSubjectSection section, Long newSchemeId) {
        Long currentSchemeId = section.getEvaluationScheme() == null ? null : section.getEvaluationScheme().getId();
        if (newSchemeId.equals(currentSchemeId)) {
            return;
        }
        if (evaluationSetupPort.hasGradesLoadedInCourseSection(section.getId())) {
            throw new SectionEvaluationSchemeLockedException(section.getId());
        }
    }
}
