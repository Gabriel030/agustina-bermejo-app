package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.policy.ChangeBillingConfigurationCommand;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescriber;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.ChangeRow;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DescriptionLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DetailBlock;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectTone;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class BillingConfigurationChangeAuthorizationRequestDescriber implements AuthorizationRequestDescriber<ChangeBillingConfigurationCommand> {
    private static final Logger log = LoggerFactory.getLogger(BillingConfigurationChangeAuthorizationRequestDescriber.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BillingConfigurationChangeRowsAssembler rowsAssembler;

    public BillingConfigurationChangeAuthorizationRequestDescriber(BillingConfigurationChangeRowsAssembler rowsAssembler) {
        this.rowsAssembler = rowsAssembler;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.CHANGE_BILLING_CONFIGURATION;
    }

    @Override
    public AuthorizationRequestDescription describe(ChangeBillingConfigurationCommand command, Long authorizationRequestId) {
        String topic = labelOf(command.topic().scope());
        List<DescriptionLine> lines = new ArrayList<>();
        lines.add(new DescriptionLine("Configuración", topic));
        lines.add(new DescriptionLine("Vigente desde", DAY.format(command.effectiveFrom())));
        if (command.changeReason() != null && !command.changeReason().isBlank()) {
            lines.add(new DescriptionLine("Motivo del cambio", command.changeReason().strip()));
        }
        List<EffectLine> effects = List.of(new EffectLine(EffectTone.WARNING, "La nueva configuración rige desde el " + DAY.format(command.effectiveFrom()) + "."));
        return new AuthorizationRequestDescription("Cambio de " + topic.toLowerCase(), null, null, lines, effects, changeBlockOf(command));
    }

    private DetailBlock changeBlockOf(ChangeBillingConfigurationCommand command) {
        try {
            List<ChangeRow> rows = rowsAssembler.assembleRows(command);
            return rows.isEmpty() ? DetailBlock.none() : DetailBlock.change(rows);
        } catch (RuntimeException e) {
            log.warn("Configuration change cannot be compared with the current values, describing it without the table: topic={}", command.topic().key(), e);
            return DetailBlock.none();
        }
    }

    static String labelOf(ConfigurationScope scope) {
        return switch (scope) {
            case YEAR_TERMS -> "Condiciones de facturación del año";
            case ANNUAL_UPFRONT -> "Pago anual adelantado";
            case COHORT_ENTRY -> "Descuento de ingreso a la cohorte";
            case COURSE_TERMS -> "Condiciones del curso";
            case PROGRAM_REMINDER_SCHEDULE -> "Recordatorios de cobranza de carreras";
            case COURSE_REMINDER_SCHEDULE -> "Recordatorios de cobranza de cursos";
            case LATE_PAYMENT -> "Recargo por mora";
            case ISSUANCE -> "Emisión de facturas";
            case ALLOCATION -> "Imputación de pagos";
            case IRREGULAR_TERMS -> "Tipos de cursada irregular";
            case COLLECTION_REMINDERS -> "Avisos de cobranza";
            case BILLING_DASHBOARD_GOALS -> "Metas del tablero de facturación";
        };
    }
}
