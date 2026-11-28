package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.dto.ExamBoardActaFile;
import com.red_escuela.backend.evaluation.dto.ExamBoardRegistrationSummaryResponse;
import com.red_escuela.backend.evaluation.dto.ExamBoardResponse;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.exception.ExamBoardCanceledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExamBoardActaExporter {

    private final ExamBoardManager examBoardManager;
    private final ExamBoardActaPdfWriter pdfWriter;

    public ExamBoardActaExporter(ExamBoardManager examBoardManager, ExamBoardActaPdfWriter pdfWriter) {
        this.examBoardManager = examBoardManager;
        this.pdfWriter = pdfWriter;
    }

    @Transactional(readOnly = true)
    public ExamBoardActaFile export(Long examBoardId, Long requiredTribunalInstructorId) {
        if (requiredTribunalInstructorId != null) {
            examBoardManager.requireTribunalMember(examBoardId, requiredTribunalInstructorId);
        }
        ExamBoard examBoard = examBoardManager.get(examBoardId);
        if (examBoard.getStatus() == ExamBoardStatus.CANCELED) {
            throw new ExamBoardCanceledException(examBoardId);
        }
        List<ExamBoardRegistrationSummaryResponse> registrants = examBoardManager.listRegistrantsWithDetail(examBoardId).stream()
                .filter(registrant -> !"WITHDRAWN".equals(registrant.status()))
                .toList();
        return new ExamBoardActaFile("acta-mesa-" + examBoardId + ".pdf", pdfWriter.write(ExamBoardResponse.from(examBoard), registrants));
    }
}
