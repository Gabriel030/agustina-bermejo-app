package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.events.AttendanceChangeRequestRejectedEvent;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RejectAttendanceChangeRequestTransaction {

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;
    private final AttendanceChangeRequestFinder finder;
    private final AttendanceChangeRequestDecisionRecorder decisionRecorder;
    private final ApplicationEventPublisher eventPublisher;

    public RejectAttendanceChangeRequestTransaction(AttendanceChangeRequestRepository attendanceChangeRequestRepository,
                                                    AttendanceChangeRequestFinder finder,
                                                    AttendanceChangeRequestDecisionRecorder decisionRecorder,
                                                    ApplicationEventPublisher eventPublisher) {
        this.attendanceChangeRequestRepository = attendanceChangeRequestRepository;
        this.finder = finder;
        this.decisionRecorder = decisionRecorder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AttendanceChangeRequest reject(Long requestId, String reason) {
        AttendanceChangeRequest request = finder.findOrThrow(requestId);
        finder.requirePending(request);
        decisionRecorder.recordRejectionByCurrentActor(request, reason);
        AttendanceChangeRequest saved = attendanceChangeRequestRepository.save(request);
        eventPublisher.publishEvent(AttendanceChangeRequestRejectedEvent.rejectedFor(saved));
        return saved;
    }
}
