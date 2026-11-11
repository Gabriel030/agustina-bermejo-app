package com.red_escuela.backend.evaluation.api;

import java.util.List;

public record ClosureNotificationTargets(String sectionLabel, String reportNumber, int reportVersion, List<Long> instructorPersonIds,
                                         List<ClosureStudentNotificationTarget> students, int promoted, int goingToFinal) {
}
