package com.red_escuela.backend.billing.configuration.exception;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.shared.exception.ConflictException;

import java.time.LocalDate;

public class NoEffectiveConfigurationException extends ConflictException {
    public NoEffectiveConfigurationException(ConfigurationTopic topic, LocalDate date) {
        super("No hay una configuración vigente para " + topic.key() + " al " + date + ".");
    }
}
