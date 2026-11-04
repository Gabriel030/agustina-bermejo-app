package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.dto.ShiftRangeRequest;
import com.red_escuela.backend.academicstructure.entity.ShiftRange;
import com.red_escuela.backend.academicstructure.exception.InvalidShiftRangesException;
import com.red_escuela.backend.academicstructure.repository.ShiftRangeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

@Service
public class ShiftRangeUpdater {

    private final ShiftRangeRepository shiftRangeRepository;

    public ShiftRangeUpdater(ShiftRangeRepository shiftRangeRepository) {
        this.shiftRangeRepository = shiftRangeRepository;
    }

    @Transactional
    public List<ShiftRange> replaceRanges(List<ShiftRangeRequest> requests) {
        var proposed = requests.stream()
                .map(r -> new ShiftRange(r.shift(), r.startTime(), r.endTime()))
                .toList();
        ensureAllShiftsOnce(proposed);
        ensureCoherent(proposed);
        return shiftRangeRepository.saveAll(proposed);
    }

    private void ensureAllShiftsOnce(List<ShiftRange> proposed) {
        var shifts = new HashSet<SectionShift>();
        proposed.forEach(r -> shifts.add(r.getShift()));
        if (shifts.size() != proposed.size() || shifts.size() != SectionShift.values().length) {
            throw new InvalidShiftRangesException("deben informarse los tres turnos, una sola vez cada uno");
        }
    }

    private void ensureCoherent(List<ShiftRange> proposed) {
        for (var range : proposed) {
            if (!range.getStartTime().isBefore(range.getEndTime())) {
                throw new InvalidShiftRangesException("el inicio de " + range.getShift() + " debe ser anterior a su fin");
            }
        }
        var ordered = proposed.stream().sorted(Comparator.comparing(ShiftRange::getStartTime)).toList();
        for (int index = 1; index < ordered.size(); index++) {
            var previous = ordered.get(index - 1);
            var current = ordered.get(index);
            if (current.getStartTime().isBefore(previous.getEndTime())) {
                throw new InvalidShiftRangesException(previous.getShift() + " y " + current.getShift() + " se solapan");
            }
        }
        var sequence = ordered.stream().map(ShiftRange::getShift).toList();
        if (!sequence.equals(List.of(SectionShift.MORNING, SectionShift.AFTERNOON, SectionShift.EVENING))) {
            throw new InvalidShiftRangesException("el orden debe ser mañana, tarde, noche");
        }
    }
}
