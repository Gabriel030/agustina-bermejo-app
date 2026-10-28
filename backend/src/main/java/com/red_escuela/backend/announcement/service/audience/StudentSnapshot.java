package com.red_escuela.backend.announcement.service.audience;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.identity.entity.Student;

import java.util.Map;

public record StudentSnapshot(
        Student student,
        EnrollmentStatus status,
        FinalGradeType finalGradeType,
        Map<Integer, Double> examScores,
        Map<Integer, Double> tpScores,
        Map<Integer, Double> finalScores,
        long absences,
        long recordedClasses) {

    public Double examScore(int sequenceNumber) {
        return examScores.get(sequenceNumber);
    }

    public Double tpScore(int sequenceNumber) {
        return tpScores.get(sequenceNumber);
    }

    public boolean tpSubmitted(int sequenceNumber) {
        return tpScores.containsKey(sequenceNumber);
    }

    public Double finalScore(int callNumber) {
        return finalScores.get(callNumber);
    }
}
