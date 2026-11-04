package com.red_escuela.backend.academicstructure.usecase.shift;

import com.red_escuela.backend.academicstructure.dto.ShiftRangeResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateShiftRangesRequest;
import com.red_escuela.backend.academicstructure.service.ShiftRangeUpdater;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class UpdateShiftRangesService implements UpdateShiftRangesUseCase {

    private final ShiftRangeUpdater shiftRangeUpdater;

    public UpdateShiftRangesService(ShiftRangeUpdater shiftRangeUpdater) {
        this.shiftRangeUpdater = shiftRangeUpdater;
    }

    @Override
    public List<ShiftRangeResponse> update(UpdateShiftRangesRequest request) {
        return shiftRangeUpdater.replaceRanges(request.ranges()).stream()
                .map(ShiftRangeResponse::from)
                .toList();
    }
}
