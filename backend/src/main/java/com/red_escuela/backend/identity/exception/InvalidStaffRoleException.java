package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidStaffRoleException extends BadRequestException {
    public InvalidStaffRoleException(String role) {
        super("Rol inválido para alta de personal: " + role);
    }
}
