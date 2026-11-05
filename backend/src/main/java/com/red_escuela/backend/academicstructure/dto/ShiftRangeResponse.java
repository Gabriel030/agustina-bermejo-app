package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.entity.ShiftRange;

import java.time.LocalTime;

public record ShiftRangeResponse(SectionShift shift, LocalTime startTime, LocalTime endTime) {

    public static ShiftRangeResponse from(ShiftRange range) {
        return new ShiftRangeResponse(range.getShift(), range.getStartTime(), range.getEndTime());
    }
}
