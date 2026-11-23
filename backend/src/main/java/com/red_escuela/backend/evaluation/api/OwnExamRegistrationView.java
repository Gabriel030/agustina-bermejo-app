package com.red_escuela.backend.evaluation.api;

import java.time.LocalDateTime;

public record OwnExamRegistrationView(Long registrationId, Long examBoardId, Long studentId, String careerName,
                                      String subjectName, LocalDateTime examDateTime, String classroom) {
}
