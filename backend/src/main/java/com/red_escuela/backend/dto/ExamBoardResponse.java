package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.ExamBoard;
import com.red_escuela.backend.entity.ExamBoardInstructor;

import java.time.LocalDateTime;
import java.util.List;
import com.red_escuela.backend.academicstructure.dto.InstructorSummaryResponse;

public record ExamBoardResponse(
        Long id, Long subjectId, String subjectName, Long courseSubjectId, String courseSubjectName, Long termId,
        LocalDateTime examDate, LocalDateTime registrationStart, LocalDateTime registrationEnd,
        String classroom, String status, List<Long> tribunalInstructorIds, List<InstructorSummaryResponse> tribunal) {

    public static ExamBoardResponse from(ExamBoard examBoard) {
        List<ExamBoardInstructor> asignaciones = examBoard.getTribunal() == null ? List.of() : examBoard.getTribunal();
        List<Long> tribunalIds = asignaciones.stream().map(a -> a.getInstructor().getId()).toList();
        List<InstructorSummaryResponse> tribunal = asignaciones.stream()
                .map(a -> InstructorSummaryResponse.from(a.getInstructor())).toList();
        return new ExamBoardResponse(
                examBoard.getId(),
                examBoard.getSubject() == null ? null : examBoard.getSubject().getId(),
                examBoard.getSubject() == null ? null : examBoard.getSubject().getName(),
                examBoard.getCourseSubject() == null ? null : examBoard.getCourseSubject().getId(),
                examBoard.getCourseSubject() == null ? null : examBoard.getCourseSubject().getName(),
                examBoard.getTerm().getId(),
                examBoard.getExamDate(),
                examBoard.getRegistrationStart(),
                examBoard.getRegistrationEnd(),
                examBoard.getClassroom(),
                examBoard.getStatus().name(),
                tribunalIds,
                tribunal);
    }
}
