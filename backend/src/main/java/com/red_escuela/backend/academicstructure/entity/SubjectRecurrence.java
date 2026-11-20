package com.red_escuela.backend.academicstructure.entity;

import org.springframework.modulith.NamedInterface;

import lombok.Getter;

@Getter
@NamedInterface("model")
public enum SubjectRecurrence {
    PROMOTIONAL("promotional"),
    ANNUAL("annual");

    private final String status;

    SubjectRecurrence(String status) {
        this.status = status;
    }
}
