package com.red_escuela.backend.shared.domain;

public enum StatusType {
    ACTIVE("active"),
    INACTIVE("inactive"),
    DELETED("deleted");

    private final String status;

    StatusType(String status) {
        this.status = status;
    }

    public String getStatus() {
        return this.name();
    }
}
