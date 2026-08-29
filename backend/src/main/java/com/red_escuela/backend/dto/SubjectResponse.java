package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.Subject;

public record SubjectResponse(Long id, String code, String name, String description, String status) {

    public static SubjectResponse from(Subject subject) {
        return new SubjectResponse(
                subject.getId(), subject.getCode(), subject.getName(), subject.getDescription(),
                subject.getStatus() == null ? null : subject.getStatus().name());
    }
}
