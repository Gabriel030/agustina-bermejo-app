package com.red_escuela.backend.enrollment.usecase.prospect;

import com.red_escuela.backend.enrollment.dto.ProspectListItemResponse;
import com.red_escuela.backend.shared.pagination.PageResponse;

public interface ListProspectsUseCase {
    PageResponse<ProspectListItemResponse> list(String search, int page, int size);
}
