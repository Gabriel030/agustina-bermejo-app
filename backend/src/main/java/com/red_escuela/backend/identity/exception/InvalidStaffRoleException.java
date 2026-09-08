package com.red_escuela.backend.identity.exception;

public class InvalidStaffRoleException extends RuntimeException {
    public InvalidStaffRoleException(String role) {
        super("Rol inválido para alta de personal: " + role);
    }
}
