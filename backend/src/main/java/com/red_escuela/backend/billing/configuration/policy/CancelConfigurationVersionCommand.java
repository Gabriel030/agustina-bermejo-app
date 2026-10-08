package com.red_escuela.backend.billing.configuration.policy;

public record CancelConfigurationVersionCommand(Long configurationVersionId, String reason, Long cancelledByPersonId) {
}
