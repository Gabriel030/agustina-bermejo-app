package com.red_escuela.backend.academicstructure.api;

import java.time.LocalDateTime;

public interface TermScheduleChangeValidator {
    void validateScheduleChange(Long termId, LocalDateTime currentStart, LocalDateTime currentEnd, LocalDateTime proposedStart, LocalDateTime proposedEnd);
}
