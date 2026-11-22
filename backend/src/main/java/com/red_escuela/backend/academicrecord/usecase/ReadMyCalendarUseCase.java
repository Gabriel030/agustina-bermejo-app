package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.dto.MyCalendarResponse;

import java.time.LocalDate;

public interface ReadMyCalendarUseCase {

    MyCalendarResponse read(Long studentId, LocalDate from, LocalDate to);

    MyCalendarResponse readEmpty(LocalDate from, LocalDate to);
}
