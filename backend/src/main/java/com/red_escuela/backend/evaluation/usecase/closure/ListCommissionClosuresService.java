package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.dto.CommissionClosurePageResponse;
import com.red_escuela.backend.evaluation.specialist.CommissionClosureListReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ListCommissionClosuresService implements ListCommissionClosuresUseCase {

    private static final int MAX_PAGE_SIZE = 50;

    private final CommissionClosureListReader listReader;

    public ListCommissionClosuresService(CommissionClosureListReader listReader) {
        this.listReader = listReader;
    }

    @Override
    public CommissionClosurePageResponse list(ListCommissionClosuresCommand command) {
        int page = Math.max(command.page(), 0);
        int size = Math.min(Math.max(command.size(), 1), MAX_PAGE_SIZE);
        return listReader.readPage(command.filter(), page, size);
    }
}
