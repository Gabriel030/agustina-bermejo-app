package com.red_escuela.backend.billing.reporting.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class UnknownMetricPanelException extends NotFoundException {

    public UnknownMetricPanelException(String panel) {
        super("No existe el panel de métricas " + panel);
    }
}
