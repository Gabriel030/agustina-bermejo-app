package com.red_escuela.backend.billing.configuration.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class ConfigurationVersionNotFoundException extends NotFoundException {
    public ConfigurationVersionNotFoundException(Long id) {
        super("Configuration version not found: " + id);
    }
}
