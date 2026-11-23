package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.events.AttendanceChangeRequestApprovedEvent;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApproveAttendanceChangeRequestTransaction {

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceChangeRequestFinder finder;
    private final AttendanceChangeRequestDecisionRecorder decisionRecorder;
    private final ApplicationEventPublisher eventPublisher;

    public ApproveAttendanceChangeRequestTransaction(AttendanceChangeRequestRepository attendanceChangeRequestRepository,
                                                     AttendanceRepository attendanceRepository,
                                                     AttendanceChangeRequestFinder finder,
                                                     AttendanceChangeRequestDecisionRecorder decisionRecorder,
                                                     ApplicationEventPublisher eventPublisher) {
        this.attendanceChangeRequestRepository = attendanceChangeRequestRepository;
        this.attendanceRepository = attendanceRepository;
        this.finder = finder;
        this.decisionRecorder = decisionRecorder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AttendanceChangeRequest approve(Long requestId) {
        AttendanceChangeRequest request = finder.findOrThrow(requestId);
        finder.requirePending(request);
        Attendance attendance = request.getAttendance();
        attendance.applyApprovedChange(request.getRequestedStatus());
        attendanceRepository.save(attendance);
        eventPublisher.publishEvent(AttendanceStatusChangedEvent.of(attendance));
        decisionRecorder.recordDecisionByCurrentActor(request, AttendanceChangeRequestStatus.APPROVED);
        AttendanceChangeRequest saved = attendanceChangeRequestRepository.save(request);
        eventPublisher.publishEvent(AttendanceChangeRequestApprovedEvent.approvedFor(saved));
        return saved;
    }
}
