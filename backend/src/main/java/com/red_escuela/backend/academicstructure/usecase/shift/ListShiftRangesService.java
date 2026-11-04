package com.red_escuela.backend.academicstructure.usecase.shift;

import com.red_escuela.backend.academicstructure.dto.ShiftRangeResponse;
import com.red_escuela.backend.academicstructure.repository.ShiftRangeRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListShiftRangesService implements ListShiftRangesUseCase {

    private final ShiftRangeRepository shiftRangeRepository;

    public ListShiftRangesService(ShiftRangeRepository shiftRangeRepository) {
        this.shiftRangeRepository = shiftRangeRepository;
    }

    @Override
    public List<ShiftRangeResponse> list() {
        return shiftRangeRepository.findAllByOrderByStartTimeAsc().stream()
                .map(ShiftRangeResponse::from)
                .toList();
    }
}
