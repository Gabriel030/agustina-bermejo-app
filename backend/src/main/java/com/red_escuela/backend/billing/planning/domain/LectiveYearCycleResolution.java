package com.red_escuela.backend.billing.planning.domain;

import java.time.YearMonth;
import java.util.Optional;

public sealed interface LectiveYearCycleResolution {

    Optional<YearCycle> installmentCycle();

    boolean hasCoveringCycle();

    boolean notConfigured();

    InvoicePlanningDecision toDecision();

    record NotConfigured(YearMonth month) implements LectiveYearCycleResolution {
        public Optional<YearCycle> installmentCycle() {
            return Optional.empty();
        }

        public boolean hasCoveringCycle() {
            return false;
        }

        public boolean notConfigured() {
            return true;
        }

        public InvoicePlanningDecision toDecision() {
            return new InvoicePlanningDecision(2, "YEAR_NOT_CONFIGURED", "la carrera no tiene año lectivo que cubra " + month);
        }
    }

    record BetweenYears() implements LectiveYearCycleResolution {
        public Optional<YearCycle> installmentCycle() {
            return Optional.empty();
        }

        public boolean hasCoveringCycle() {
            return false;
        }

        public boolean notConfigured() {
            return false;
        }

        public InvoicePlanningDecision toDecision() {
            return new InvoicePlanningDecision(2, "BETWEEN_YEARS", "entre dos años lectivos configurados");
        }
    }

    record Configured(YearCycle cycle, Optional<YearCycle> installmentCycle, boolean hasCoveringCycle) implements LectiveYearCycleResolution {
        public boolean notConfigured() {
            return false;
        }

        public InvoicePlanningDecision toDecision() {
            return new InvoicePlanningDecision(2, "CYCLE", "plantilla " + cycle.templateId() + " con " + cycle.slotCount() + " matrícula(s)");
        }
    }
}
