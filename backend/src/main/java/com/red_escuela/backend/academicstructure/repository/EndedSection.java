package com.red_escuela.backend.academicstructure.repository;

import java.time.LocalDateTime;

public record EndedSection(Long sectionId, LocalDateTime termEnd) {
}
