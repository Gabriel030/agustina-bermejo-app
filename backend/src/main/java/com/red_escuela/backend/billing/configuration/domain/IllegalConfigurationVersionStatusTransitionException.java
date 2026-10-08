package com.red_escuela.backend.billing.configuration.domain;

import com.red_escuela.backend.shared.exception.ConflictException;

public class IllegalConfigurationVersionStatusTransitionException extends ConflictException {
    public IllegalConfigurationVersionStatusTransitionException(ConfigurationVersionStatus from, ConfigurationVersionEvent event) {
        super("Configuration version in status " + from + " does not accept " + event);
    }
}
