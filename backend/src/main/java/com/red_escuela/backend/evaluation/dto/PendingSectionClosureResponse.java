package com.red_escuela.backend.evaluation.dto;

import java.time.LocalDate;

public record PendingSectionClosureResponse(String sectionKind, Long sectionId, String sectionLabel, LocalDate termEnd,
                                            long daysAfterTermEnd, boolean graceOver, PendingClosureState closureState,
                                            boolean hasPublishedGrades) {
}
