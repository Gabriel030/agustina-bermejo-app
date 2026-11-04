package com.red_escuela.backend.academicstructure.usecase.shift;

import com.red_escuela.backend.academicstructure.dto.ShiftRangeResponse;
import com.red_escuela.backend.academicstructure.entity.ShiftRange;
import com.red_escuela.backend.academicstructure.repository.ShiftRangeRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@UseCase
public class ListShiftRangesService implements ListShiftRangesUseCase {

    private final ShiftRangeRepository shiftRangeRepository;

    public ListShiftRangesService(ShiftRangeRepository shiftRangeRepository) {
        this.shiftRangeRepository = shiftRangeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftRangeResponse> list() {
        return shiftRangeRepository.findAll().stream()
                .sorted(Comparator.comparing(ShiftRange::getStartTime))
                .map(ShiftRangeResponse::from)
                .toList();
    }
}
