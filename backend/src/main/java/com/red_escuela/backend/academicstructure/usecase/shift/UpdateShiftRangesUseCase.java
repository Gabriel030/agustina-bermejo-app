package com.red_escuela.backend.academicstructure.usecase.shift;

import com.red_escuela.backend.academicstructure.dto.ShiftRangeResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateShiftRangesRequest;

import java.util.List;

public interface UpdateShiftRangesUseCase {
    List<ShiftRangeResponse> update(UpdateShiftRangesRequest request);
}
