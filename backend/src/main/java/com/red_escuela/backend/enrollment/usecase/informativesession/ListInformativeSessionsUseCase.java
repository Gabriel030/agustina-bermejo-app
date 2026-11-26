package com.red_escuela.backend.enrollment.usecase.informativesession;

import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;
import com.red_escuela.backend.enrollment.dto.ListInformativeSessionsQuery;

import java.util.List;

public interface ListInformativeSessionsUseCase {
    List<InformativeSessionResponse> list(ListInformativeSessionsQuery query);
}
