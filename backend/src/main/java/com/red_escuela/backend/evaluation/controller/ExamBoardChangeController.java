package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.evaluation.dto.CancelExamBoardRequest;
import com.red_escuela.backend.evaluation.dto.ExamBoardResponse;
import com.red_escuela.backend.evaluation.dto.RescheduleExamBoardRequest;
import com.red_escuela.backend.evaluation.usecase.examboard.CancelExamBoardCommand;
import com.red_escuela.backend.evaluation.usecase.examboard.CancelExamBoardUseCase;
import com.red_escuela.backend.evaluation.usecase.examboard.RescheduleExamBoardCommand;
import com.red_escuela.backend.evaluation.usecase.examboard.RescheduleExamBoardUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExamBoardChangeController {

    private final RescheduleExamBoardUseCase rescheduleExamBoard;
    private final CancelExamBoardUseCase cancelExamBoard;

    public ExamBoardChangeController(RescheduleExamBoardUseCase rescheduleExamBoard, CancelExamBoardUseCase cancelExamBoard) {
        this.rescheduleExamBoard = rescheduleExamBoard;
        this.cancelExamBoard = cancelExamBoard;
    }

    @PostMapping("/exam-boards/{id}/reschedule")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ExamBoardResponse reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleExamBoardRequest request) {
        return ExamBoardResponse.from(rescheduleExamBoard.reschedule(new RescheduleExamBoardCommand(id, request)));
    }

    @PostMapping("/exam-boards/{id}/cancel")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ExamBoardResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelExamBoardRequest request) {
        return ExamBoardResponse.from(cancelExamBoard.cancel(new CancelExamBoardCommand(id, request.reason())));
    }
}
