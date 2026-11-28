package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.dto.AttendanceChangeRequestResponse;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevertAttendanceChangeRequestDecisionTransaction {

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceChangeRequestFinder finder;
    private final AttendanceDecisionRevertPolicy revertPolicy;
    private final AttendanceActorResolver actorResolver;
    private final AuditTrail auditTrail;
    private final ApplicationEventPublisher eventPublisher;
    private final AttendanceChangeRequestResponseAssembler responseAssembler;

    public RevertAttendanceChangeRequestDecisionTransaction(AttendanceChangeRequestRepository attendanceChangeRequestRepository,
                                                            AttendanceRepository attendanceRepository,
                                                            AttendanceChangeRequestFinder finder,
                                                            AttendanceDecisionRevertPolicy revertPolicy,
                                                            AttendanceActorResolver actorResolver,
                                                            AuditTrail auditTrail,
                                                            ApplicationEventPublisher eventPublisher,
                                                            AttendanceChangeRequestResponseAssembler responseAssembler) {
        this.attendanceChangeRequestRepository = attendanceChangeRequestRepository;
        this.attendanceRepository = attendanceRepository;
        this.finder = finder;
        this.revertPolicy = revertPolicy;
        this.actorResolver = actorResolver;
        this.auditTrail = auditTrail;
        this.eventPublisher = eventPublisher;
        this.responseAssembler = responseAssembler;
    }

    @Transactional
    public AttendanceChangeRequestResponse revert(Long requestId, String reason) {
        AttendanceChangeRequest request = finder.findOrThrow(requestId);
        revertPolicy.requireRevertible(request);
        AttendanceChangeRequestStatus revertedDecision = request.getStatus();
        if (revertedDecision == AttendanceChangeRequestStatus.APPROVED) {
            Attendance attendance = request.getAttendance();
            attendance.revertApprovedChange(request.getPreviousStatus());
            attendanceRepository.save(attendance);
            eventPublisher.publishEvent(AttendanceStatusChangedEvent.of(attendance));
        }
        request.reopenReverted(actorResolver.currentActor(), reason.trim(), ApplicationTime.now());
        AttendanceChangeRequest saved = attendanceChangeRequestRepository.save(request);
        auditTrail.record(new AuditRecord(AuditAction.ATTENDANCE_CHANGE_DECISION_REVERTED,
                AuditEntityType.ATTENDANCE_CHANGE_REQUEST, saved.getId(),
                revertedDecision.name(), AttendanceChangeRequestStatus.PENDING.name(), reason.trim()));
        return responseAssembler.assemble(saved);
    }
}
