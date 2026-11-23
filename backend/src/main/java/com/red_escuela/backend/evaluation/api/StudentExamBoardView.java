package com.red_escuela.backend.evaluation.api;

import java.time.LocalDateTime;

public record StudentExamBoardView(Long boardId, String subjectName, LocalDateTime examDateTime, String classroom,
                                   String boardStatus, String registrationStatus, boolean registrationOpen) {
}
