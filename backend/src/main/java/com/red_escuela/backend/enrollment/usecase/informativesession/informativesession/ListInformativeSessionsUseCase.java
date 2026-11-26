package com.red_escuela.backend.enrollment.usecase.informativesession;

import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;
import com.red_escuela.backend.enrollment.entity.InformativeSessionStatus;

import java.time.LocalDate;
import java.util.List;

public interface ListInformativeSessionsUseCase {
    List<InformativeSessionResponse> list(InformativeSessionStatus status, LocalDate from);
}
