package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.exception.SlotOutsideShiftException;
import com.red_escuela.backend.academicstructure.repository.ShiftRangeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

@Service
public class SectionShiftSlotGuard {

    private final ShiftRangeRepository shiftRangeRepository;

    public SectionShiftSlotGuard(ShiftRangeRepository shiftRangeRepository) {
        this.shiftRangeRepository = shiftRangeRepository;
    }

    public void ensureInsideShift(SectionShift shift, LocalTime start, LocalTime end) {
        if (shift == null) {
            return;
        }
        var range = shiftRangeRepository.findById(shift).orElse(null);
        if (range != null && !range.contains(start, end)) {
            throw new SlotOutsideShiftException(shift, start, end, range.getStartTime(), range.getEndTime());
        }
    }
}
