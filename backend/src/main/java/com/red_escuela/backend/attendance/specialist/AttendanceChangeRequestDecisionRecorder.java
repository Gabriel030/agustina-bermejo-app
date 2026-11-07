package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Service;

@Service
public class AttendanceChangeRequestDecisionRecorder {

    private final AttendanceActorResolver actorResolver;

    public AttendanceChangeRequestDecisionRecorder(AttendanceActorResolver actorResolver) {
        this.actorResolver = actorResolver;
    }

    public void recordDecisionByCurrentActor(AttendanceChangeRequest request, AttendanceChangeRequestStatus decision) {
        request.setStatus(decision);
        request.setApprovedBy(actorResolver.currentActor());
        request.setApprovedAt(ApplicationTime.now());
    }

    public void recordRejectionByCurrentActor(AttendanceChangeRequest request, String reason) {
        recordDecisionByCurrentActor(request, AttendanceChangeRequestStatus.REJECTED);
        request.setRejectionReason(reason.trim());
    }
}
