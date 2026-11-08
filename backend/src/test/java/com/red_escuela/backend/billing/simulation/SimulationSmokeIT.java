package com.red_escuela.backend.billing.simulation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static com.red_escuela.backend.billing.simulation.Ledger.Row;

@SimulationTest
class SimulationSmokeIT extends AbstractSimulationTest {

    private static Row enrollmentFee() {
        return Row.of("ENROLLMENT_FEE", YearMonth.of(2027, 3), LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 1), "60000.00", "0.00", "ISSUED", "PAID");
    }

    private static Row installment(int month, String outstanding, String paymentStatus) {
        return Row.of("MONTHLY_INSTALLMENT", YearMonth.of(2027, month), LocalDate.of(2027, month, 1), LocalDate.of(2027, month, 11), "48000.00", outstanding, "ISSUED",
                paymentStatus);
    }

    @Test
    @Tag("D-001")
    @Tag("D-021")
    @Tag("D-099")
    void smokeThreeMonthsOnTimeStudentKeepsAllInvariants() {
        Scenario scenario = scenario(Timeline.Mode.SPARSE);

        scenario.enrollStudent(LocalDate.of(2027, 3, 1));
        scenario.ledger().hasExactly(
                Row.of("ENROLLMENT_FEE", YearMonth.of(2027, 3), LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 1), "60000.00", "60000.00", "ISSUED", "PENDING"),
                installment(3, "48000.00", "PENDING"));

        scenario.advanceTo(LocalDate.of(2027, 3, 5)).payEverythingDue();
        scenario.advanceTo(LocalDate.of(2027, 4, 5)).payEverythingDue();
        scenario.advanceTo(LocalDate.of(2027, 5, 5)).payEverythingDue();
        scenario.advanceTo(LocalDate.of(2027, 6, 1)).finish();

        scenario.ledger().hasExactly(enrollmentFee(), installment(3, "0.00", "PAID"), installment(4, "0.00", "PAID"), installment(5, "0.00", "PAID"),
                installment(6, "48000.00", "PENDING"));
    }
}
