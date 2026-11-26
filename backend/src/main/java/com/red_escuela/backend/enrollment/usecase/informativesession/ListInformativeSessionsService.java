package com.red_escuela.backend.enrollment.usecase.informativesession;

import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;
import com.red_escuela.backend.enrollment.dto.ListInformativeSessionsQuery;
import com.red_escuela.backend.enrollment.service.InformativeSessionReader;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListInformativeSessionsService implements ListInformativeSessionsUseCase {

    private final InformativeSessionReader informativeSessionReader;

    public ListInformativeSessionsService(InformativeSessionReader informativeSessionReader) {
        this.informativeSessionReader = informativeSessionReader;
    }

    @Override
    public List<InformativeSessionResponse> list(ListInformativeSessionsQuery query) {
        return informativeSessionReader.list(query);
    }
}
