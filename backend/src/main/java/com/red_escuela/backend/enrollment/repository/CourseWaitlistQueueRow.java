package com.red_escuela.backend.enrollment.repository;

import java.time.LocalDateTime;

public interface CourseWaitlistQueueRow extends WaitlistQueueRow {
    LocalDateTime getTermStart();

    Integer getWaitlistDeadlineOffsetDays();
}
