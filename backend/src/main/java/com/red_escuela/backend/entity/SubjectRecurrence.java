package com.red_escuela.backend.entity;

import lombok.Getter;

@Getter
public enum SubjectRecurrence {
    PROMOTIONAL("promotional"),
    ANNUAL("annual");

    private final String status;

    SubjectRecurrence(String status) {
        this.status = status;
    }
}
