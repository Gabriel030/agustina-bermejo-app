package com.red_escuela.backend.shared.permission;

public interface PermissionDiscrepancySink {

    void record(String role, String permission, boolean legacyDecision, boolean permissionDecision);
}
