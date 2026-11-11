package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ExamBoardFinalGradesRequest(@NotEmpty @Valid List<ExamBoardFinalGradeEntryRequest> items) implements SelfValidatingRequest {
    public static final int MAX_ITEMS = 200;

    public ExamBoardFinalGradesRequest {
        RequestLimits.requireMaxSize("items", items, MAX_ITEMS);
    }
}
