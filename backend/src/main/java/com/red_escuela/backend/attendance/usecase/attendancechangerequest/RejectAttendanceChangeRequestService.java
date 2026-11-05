package com.red_escuela.backend.attendance.usecase.attendancechangerequest;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import com.red_escuela.backend.attendance.service.AttendanceChangeRequestFinder;
import com.red_escuela.backend.attendance.service.AttendanceChangeRequestDecisionRecorder;
import com.red_escuela.backend.attendance.events.AttendanceChangeRequestRejectedEvent;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;


@UseCase
public class RejectAttendanceChangeRequestService implements RejectAttendanceChangeRequestUseCase {

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;
    private final AttendanceChangeRequestFinder finder;
    private final AttendanceChangeRequestDecisionRecorder decisionRecorder;
    private final ApplicationEventPublisher eventPublisher;

    public RejectAttendanceChangeRequestService(AttendanceChangeRequestRepository attendanceChangeRequestRepository,
                                                 AttendanceChangeRequestFinder finder,
                                                 AttendanceChangeRequestDecisionRecorder decisionRecorder,
                                                 ApplicationEventPublisher eventPublisher) {
        this.attendanceChangeRequestRepository = attendanceChangeRequestRepository;
        this.finder = finder;
        this.decisionRecorder = decisionRecorder;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public AttendanceChangeRequest reject(Long id) {
        AttendanceChangeRequest request = finder.findOrThrow(id);
        finder.requirePending(request);
        decisionRecorder.recordDecisionByCurrentActor(request, AttendanceChangeRequestStatus.REJECTED);
        AttendanceChangeRequest saved = attendanceChangeRequestRepository.save(request);
        eventPublisher.publishEvent(AttendanceChangeRequestRejectedEvent.rejectedFor(saved));
        return saved;
    }
}
