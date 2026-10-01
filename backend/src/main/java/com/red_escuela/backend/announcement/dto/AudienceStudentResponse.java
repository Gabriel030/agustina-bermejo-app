package com.red_escuela.backend.announcement.dto;

import com.red_escuela.backend.announcement.service.audience.StudentSnapshot;

import java.util.Map;

public record AudienceStudentResponse(
        Long studentId,
        String file,
        String name,
        Map<Integer, Double> examScores,
        Map<Integer, Double> tpScores,
        Map<Integer, Double> finalScores,
        long absences) {

    public static AudienceStudentResponse from(StudentSnapshot snapshot) {
        return new AudienceStudentResponse(
                snapshot.student().getId(),
                snapshot.student().getPerson().getFile(),
                snapshot.student().getPerson().getName() + " " + snapshot.student().getPerson().getLastname(),
                snapshot.examScores(),
                snapshot.tpScores(),
                snapshot.finalScores(),
                snapshot.absences());
    }
}
