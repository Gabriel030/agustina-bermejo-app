package com.red_escuela.backend.attendance.usecase.attendancechangerequest;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.service.AttendanceChangeRequestFinder;
import com.red_escuela.backend.attendance.service.AttendanceChangeRequestDecisionRecorder;
import com.red_escuela.backend.attendance.events.AttendanceChangeRequestApprovedEvent;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import org.springframework.transaction.annotation.Transactional;


@UseCase
public class ApproveAttendanceChangeRequestService implements ApproveAttendanceChangeRequestUseCase {

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceChangeRequestFinder finder;
    private final AttendanceChangeRequestDecisionRecorder decisionRecorder;
    private final ApplicationEventPublisher eventPublisher;

    public ApproveAttendanceChangeRequestService(AttendanceChangeRequestRepository attendanceChangeRequestRepository,
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

    @Override
    @Transactional
    public AttendanceChangeRequest approve(Long id) {
        AttendanceChangeRequest request = finder.findOrThrow(id);
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
