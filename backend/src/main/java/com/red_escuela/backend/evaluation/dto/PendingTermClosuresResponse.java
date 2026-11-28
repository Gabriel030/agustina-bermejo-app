package com.red_escuela.backend.evaluation.dto;

import java.util.List;

public record PendingTermClosuresResponse(int graceDays, List<PendingSectionClosureResponse> sections,
                                          List<PendingExamBoardClosureResponse> examBoards) {
}
