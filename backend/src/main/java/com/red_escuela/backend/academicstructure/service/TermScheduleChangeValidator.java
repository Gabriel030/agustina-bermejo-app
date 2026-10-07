package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.Term;

import java.time.LocalDateTime;

public interface TermScheduleChangeValidator {
    void validateScheduleChange(Term term, LocalDateTime proposedStart, LocalDateTime proposedEnd);
}
