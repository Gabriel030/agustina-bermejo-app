package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.exception.SlotOutsideShiftException;
import com.red_escuela.backend.academicstructure.repository.ShiftRangeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.Optional;

@Service
public class SectionShiftSlotGuard {

    private final ShiftRangeRepository shiftRangeRepository;

    public SectionShiftSlotGuard(ShiftRangeRepository shiftRangeRepository) {
        this.shiftRangeRepository = shiftRangeRepository;
    }

    public void ensureInsideShift(SectionShift shift, LocalTime start, LocalTime end) {
        findViolation(shift, start, end).ifPresent(violation -> {
            throw violation;
        });
    }

    public Optional<SlotOutsideShiftException> findViolation(SectionShift shift, LocalTime start, LocalTime end) {
        if (shift == null) {
            return Optional.empty();
        }
        return shiftRangeRepository.findById(shift)
                .filter(range -> !range.contains(start, end))
                .map(range -> new SlotOutsideShiftException(shift, start, end, range.getStartTime(), range.getEndTime()));
    }
}
