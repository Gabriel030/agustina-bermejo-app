package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.evaluation.dto.CancelExamBoardRequest;
import com.red_escuela.backend.evaluation.dto.ExamBoardResponse;
import com.red_escuela.backend.evaluation.dto.RescheduleExamBoardRequest;
import com.red_escuela.backend.evaluation.usecase.examboard.CancelExamBoardCommand;
import com.red_escuela.backend.evaluation.usecase.examboard.CancelExamBoardUseCase;
import com.red_escuela.backend.evaluation.usecase.examboard.RescheduleExamBoardCommand;
import com.red_escuela.backend.evaluation.usecase.examboard.RescheduleExamBoardUseCase;
import jakarta.validation.Valid;
import com.red_escuela.backend.shared.permission.RequiresPermission;
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
    @RequiresPermission(code = "evaluation.manage_exam_boards", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO")
    public ExamBoardResponse reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleExamBoardRequest request) {
        return rescheduleExamBoard.reschedule(new RescheduleExamBoardCommand(id, request));
    }

    @PostMapping("/exam-boards/{id}/cancel")
    @RequiresPermission(code = "evaluation.cancel_exam_board", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO")
    public ExamBoardResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelExamBoardRequest request) {
        return cancelExamBoard.cancel(new CancelExamBoardCommand(id, request.reason()));
    }
}
