package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.dto.PendingTermClosuresResponse;
import com.red_escuela.backend.evaluation.specialist.TermEndClosureReview;
import com.red_escuela.backend.shared.UseCase;

import java.time.Clock;
import java.time.LocalDate;

@UseCase
public class ListPendingTermClosuresService implements ListPendingTermClosuresUseCase {

    private final TermEndClosureReview closureReview;
    private final Clock clock;

    public ListPendingTermClosuresService(TermEndClosureReview closureReview, Clock clock) {
        this.closureReview = closureReview;
        this.clock = clock;
    }

    @Override
    public PendingTermClosuresResponse listPendingTermClosures() {
        return closureReview.review(LocalDate.now(clock));
    }
}
