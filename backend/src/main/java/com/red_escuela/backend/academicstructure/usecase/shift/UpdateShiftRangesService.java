package com.red_escuela.backend.academicstructure.usecase.shift;

import com.red_escuela.backend.academicstructure.dto.ShiftRangeResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateShiftRangesRequest;
import com.red_escuela.backend.academicstructure.entity.ShiftRange;
import com.red_escuela.backend.academicstructure.service.ShiftRangeUpdater;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@UseCase
public class UpdateShiftRangesService implements UpdateShiftRangesUseCase {

    private final ShiftRangeUpdater shiftRangeUpdater;

    public UpdateShiftRangesService(ShiftRangeUpdater shiftRangeUpdater) {
        this.shiftRangeUpdater = shiftRangeUpdater;
    }

    @Override
    @Transactional
    public List<ShiftRangeResponse> update(UpdateShiftRangesRequest request) {
        return shiftRangeUpdater.replaceRanges(request.ranges()).stream()
                .sorted(Comparator.comparing(ShiftRange::getStartTime))
                .map(ShiftRangeResponse::from)
                .toList();
    }
}
