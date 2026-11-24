package com.red_escuela.backend.enrollment.usecase.waitlist;

import com.red_escuela.backend.enrollment.dto.OwnWaitlistPositionResponse;

import java.util.List;

public interface ListOwnWaitlistPositionsUseCase {
    List<OwnWaitlistPositionResponse> list(Long studentId);
}
