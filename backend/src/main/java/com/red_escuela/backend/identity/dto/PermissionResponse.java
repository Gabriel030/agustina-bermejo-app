package com.red_escuela.backend.identity.dto;

public record PermissionResponse(String code, String description, boolean fixedToDirector, boolean sensitive) {
}
