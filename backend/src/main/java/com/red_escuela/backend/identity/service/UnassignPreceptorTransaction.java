package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.PreceptorAssignment;
import com.red_escuela.backend.identity.exception.PreceptorAssignmentNotFoundException;
import com.red_escuela.backend.identity.repository.PreceptorAssignmentRepository;
import com.red_escuela.backend.identity.specialist.IdentityAuditRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnassignPreceptorTransaction {

    private final PreceptorAssignmentRepository preceptorAssignmentRepository;
    private final IdentityAuditRecorder identityAuditRecorder;

    public UnassignPreceptorTransaction(PreceptorAssignmentRepository preceptorAssignmentRepository,
                                        IdentityAuditRecorder identityAuditRecorder) {
        this.preceptorAssignmentRepository = preceptorAssignmentRepository;
        this.identityAuditRecorder = identityAuditRecorder;
    }

    @Transactional
    public void unassign(Long assignmentId) {
        PreceptorAssignment assignment = preceptorAssignmentRepository.findById(assignmentId)
                .filter(found -> found.getSubjectSectionId() != null || found.getCourseSectionId() != null)
                .orElseThrow(() -> new PreceptorAssignmentNotFoundException(assignmentId));
        preceptorAssignmentRepository.delete(assignment);
        identityAuditRecorder.recordPreceptorUnassigned(assignment);
    }
}
