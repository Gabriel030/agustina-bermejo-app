package com.red_escuela.backend.graduation.specialist;

import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class GraduationRequestEnrollmentCloser {

    private static final List<GraduationRequestStatus> OPEN_STATUSES =
            List.of(GraduationRequestStatus.REQUESTED, GraduationRequestStatus.ADMIN_VERIFIED);

    private final GraduationRequestRepository repository;
    private final Clock clock;

    public GraduationRequestEnrollmentCloser(GraduationRequestRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void closeOpenRequests(Long programEnrollmentId, String reason) {
        LocalDateTime now = LocalDateTime.now(clock);
        repository.findByProgramEnrollmentIdAndStatusIn(programEnrollmentId, OPEN_STATUSES).forEach(request -> {
            request.closeBecauseEnrollmentEnded(reason, now);
            repository.save(request);
        });
    }
}
