package com.red_escuela.backend.academicstructure.usecase.shift;

import com.red_escuela.backend.academicstructure.dto.ShiftRangeResponse;

import java.util.List;

public interface ListShiftRangesUseCase {
    List<ShiftRangeResponse> list();
}
