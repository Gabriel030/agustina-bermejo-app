package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.api.PreceptorAssignmentTargetPort;
import com.red_escuela.backend.identity.dto.AssignPreceptorCommand;
import com.red_escuela.backend.identity.dto.ListPreceptorAssignmentsCommand;
import com.red_escuela.backend.identity.dto.PreceptorAssignmentResult;
import com.red_escuela.backend.identity.dto.ReplaceSectionPreceptorsCommand;
import com.red_escuela.backend.identity.entity.PreceptorAssignment;
import com.red_escuela.backend.identity.exception.PreceptorAssignmentSectionNotFoundException;
import com.red_escuela.backend.identity.exception.PreceptorAssignmentTargetInvalidException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ReplaceSectionPreceptorsTransaction {

    private final PreceptorAssignmentTargetPort preceptorAssignmentTargetPort;
    private final ListPreceptorAssignmentsTransaction listPreceptorAssignmentsTransaction;
    private final AssignPreceptorTransaction assignPreceptorTransaction;
    private final UnassignPreceptorTransaction unassignPreceptorTransaction;

    public ReplaceSectionPreceptorsTransaction(PreceptorAssignmentTargetPort preceptorAssignmentTargetPort,
                                               ListPreceptorAssignmentsTransaction listPreceptorAssignmentsTransaction,
                                               AssignPreceptorTransaction assignPreceptorTransaction,
                                               UnassignPreceptorTransaction unassignPreceptorTransaction) {
        this.preceptorAssignmentTargetPort = preceptorAssignmentTargetPort;
        this.listPreceptorAssignmentsTransaction = listPreceptorAssignmentsTransaction;
        this.assignPreceptorTransaction = assignPreceptorTransaction;
        this.unassignPreceptorTransaction = unassignPreceptorTransaction;
    }

    @Transactional
    public List<PreceptorAssignmentResult> replace(ReplaceSectionPreceptorsCommand command) {
        if ((command.subjectSectionId() == null) == (command.courseSectionId() == null)) {
            throw new PreceptorAssignmentTargetInvalidException();
        }
        requireSectionExists(command);
        Set<Long> desired = new LinkedHashSet<>(command.personIds());
        ListPreceptorAssignmentsCommand listCommand =
                new ListPreceptorAssignmentsCommand(command.subjectSectionId(), command.courseSectionId());
        List<PreceptorAssignmentResult> current = listPreceptorAssignmentsTransaction.list(listCommand);
        Set<Long> alreadyAssigned = new LinkedHashSet<>();
        current.forEach(result -> alreadyAssigned.add(result.assignment().getPersonId()));
        for (Long personId : desired) {
            if (!alreadyAssigned.contains(personId)) {
                assignPreceptorTransaction.assign(new AssignPreceptorCommand(personId,
                        command.subjectSectionId(), command.courseSectionId(), command.actorId()));
            }
        }
        for (PreceptorAssignmentResult result : current) {
            PreceptorAssignment assignment = result.assignment();
            if (!desired.contains(assignment.getPersonId())) {
                unassignPreceptorTransaction.unassign(assignment.getId());
            }
        }
        return listPreceptorAssignmentsTransaction.list(listCommand);
    }

    private void requireSectionExists(ReplaceSectionPreceptorsCommand command) {
        if (command.subjectSectionId() != null
                && !preceptorAssignmentTargetPort.subjectSectionExists(command.subjectSectionId())) {
            throw new PreceptorAssignmentSectionNotFoundException(command.subjectSectionId());
        }
        if (command.courseSectionId() != null
                && !preceptorAssignmentTargetPort.courseSectionExists(command.courseSectionId())) {
            throw new PreceptorAssignmentSectionNotFoundException(command.courseSectionId());
        }
    }
}
