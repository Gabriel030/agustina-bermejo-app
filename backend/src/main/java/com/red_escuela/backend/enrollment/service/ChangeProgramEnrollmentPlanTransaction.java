package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.CurriculumPlanEnrollmentLock;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.dto.ChangeProgramEnrollmentPlanRequest;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentPlanChangeResponse;
import com.red_escuela.backend.enrollment.dto.RecordSubjectEquivalenceRequest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentPlanMismatchException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChangeProgramEnrollmentPlanTransaction {

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CurriculumPlanEnrollmentLock curriculumPlanEnrollmentLock;
    private final RecordSubjectEquivalenceTransaction recordSubjectEquivalenceTransaction;

    public ChangeProgramEnrollmentPlanTransaction(ProgramEnrollmentRepository programEnrollmentRepository,
                                                  CurriculumPlanEnrollmentLock curriculumPlanEnrollmentLock,
                                                  RecordSubjectEquivalenceTransaction recordSubjectEquivalenceTransaction) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.curriculumPlanEnrollmentLock = curriculumPlanEnrollmentLock;
        this.recordSubjectEquivalenceTransaction = recordSubjectEquivalenceTransaction;
    }

    @Transactional
    public ProgramEnrollmentPlanChangeResponse change(Long programEnrollmentId, ChangeProgramEnrollmentPlanRequest request) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findByIdForUpdate(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        enrollment.requireActive();
        CurriculumPlan targetPlan = curriculumPlanEnrollmentLock.lockOpenForNewStudents(request.targetPlanId());
        Long programId = enrollment.getProgram().getId();
        if (!targetPlan.getProgram().getId().equals(programId)) {
            throw new ProgramEnrollmentPlanMismatchException(targetPlan.getId(), programId);
        }
        enrollment.setPlan(targetPlan);
        programEnrollmentRepository.saveAndFlush(enrollment);
        List<Long> equivalenceIds = new ArrayList<>();
        for (RecordSubjectEquivalenceRequest equivalence : request.equivalences()) {
            equivalenceIds.add(recordSubjectEquivalenceTransaction.record(programEnrollmentId, equivalence));
        }
        return new ProgramEnrollmentPlanChangeResponse(programEnrollmentId, targetPlan.getId(), equivalenceIds);
    }
}
