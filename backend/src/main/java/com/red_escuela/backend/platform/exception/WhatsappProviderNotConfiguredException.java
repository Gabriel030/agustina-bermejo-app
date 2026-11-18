package com.red_escuela.backend.platform.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class WhatsappProviderNotConfiguredException extends ConflictException {
    public WhatsappProviderNotConfiguredException(String provider) {
        super("El proveedor de WhatsApp no está configurado (proveedor elegido: " + provider + ")");
    }
}
