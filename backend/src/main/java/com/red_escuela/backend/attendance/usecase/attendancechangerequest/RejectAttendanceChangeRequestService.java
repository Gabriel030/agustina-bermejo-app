package com.red_escuela.backend.attendance.usecase.attendancechangerequest;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import com.red_escuela.backend.attendance.service.AttendanceChangeRequestFinder;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.notification.AttendanceChangeRequestRejectedEvent;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class RejectAttendanceChangeRequestService implements RejectAttendanceChangeRequestUseCase {

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;
    private final AttendanceChangeRequestFinder finder;
    private final CurrentPersonService currentPersonService;
    private final ApplicationEventPublisher eventPublisher;

    public RejectAttendanceChangeRequestService(AttendanceChangeRequestRepository attendanceChangeRequestRepository,
                                                 AttendanceChangeRequestFinder finder,
                                                 CurrentPersonService currentPersonService,
                                                 ApplicationEventPublisher eventPublisher) {
        this.attendanceChangeRequestRepository = attendanceChangeRequestRepository;
        this.finder = finder;
        this.currentPersonService = currentPersonService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public AttendanceChangeRequest reject(Long id) {
        AttendanceChangeRequest request = finder.findOrThrow(id);
        finder.requirePending(request);

        request.setStatus(AttendanceChangeRequestStatus.REJECTED);
        request.setApprovedBy(currentPersonService.resolveCurrentPerson());
        request.setApprovedAt(LocalDateTime.now());
        AttendanceChangeRequest saved = attendanceChangeRequestRepository.save(request);

        eventPublisher.publishEvent(new AttendanceChangeRequestRejectedEvent(saved, saved.getRequestedBy().getPerson()));
        return saved;
    }
}
