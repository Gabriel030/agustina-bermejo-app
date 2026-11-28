package com.red_escuela.backend.evaluation.dto;

import java.time.LocalDateTime;

public record PendingExamBoardClosureResponse(Long examBoardId, String subjectName, LocalDateTime examDate,
                                              long daysAfterExamDate, boolean graceOver, long registeredCount,
                                              boolean withoutTribunal) {
}
