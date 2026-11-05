package com.red_escuela.backend.enrollment.usecase.prospect;

import com.red_escuela.backend.enrollment.dto.ProspectListItemResponse;

import java.util.List;

public interface ListProspectsUseCase {
    List<ProspectListItemResponse> list();
}
