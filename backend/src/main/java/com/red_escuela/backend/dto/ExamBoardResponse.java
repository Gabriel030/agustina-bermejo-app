package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.ExamBoard;
import com.red_escuela.backend.entity.ExamBoardInstructor;

import java.time.LocalDateTime;
import java.util.List;

public record ExamBoardResponse(
        Long id, Long subjectId, String subjectName, Long courseSubjectId, Long termId,
        LocalDateTime examDate, LocalDateTime registrationStart, LocalDateTime registrationEnd,
        String classroom, String status, List<Long> tribunalInstructorIds) {

    public static ExamBoardResponse from(ExamBoard examBoard) {
        List<Long> tribunal = examBoard.getTribunal() == null
                ? List.of()
                : examBoard.getTribunal().stream().map(ExamBoardInstructor::getInstructor)
                        .map(instructor -> instructor.getId()).toList();
        return new ExamBoardResponse(
                examBoard.getId(),
                examBoard.getSubject() == null ? null : examBoard.getSubject().getId(),
                examBoard.getSubject() == null ? null : examBoard.getSubject().getName(),
                examBoard.getCourseSubject() == null ? null : examBoard.getCourseSubject().getId(),
                examBoard.getTerm().getId(),
                examBoard.getExamDate(),
                examBoard.getRegistrationStart(),
                examBoard.getRegistrationEnd(),
                examBoard.getClassroom(),
                examBoard.getStatus().name(),
                tribunal);
    }
}
