package com.red_escuela.backend.billing.simulation;

import com.red_escuela.backend.billing.payment.domain.CashPaymentPreview;
import com.red_escuela.backend.billing.payment.dto.RegisterCashPaymentRequest;
import com.red_escuela.backend.billing.payment.usecase.PreviewCashPaymentUseCase;
import com.red_escuela.backend.billing.payment.usecase.RegisterCashPaymentUseCase;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.time.MutableClock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;

public final class Scenario {

    private final Fixtures fixtures;
    private final Fixtures.Catalog catalog;
    private final Timeline timeline;
    private final SimulationInvariants invariants;
    private final JdbcTemplate jdbc;
    private final MutableClock clock;
    private final PreviewCashPaymentUseCase previewCashPayment;
    private final RegisterCashPaymentUseCase registerCashPayment;
    private Fixtures.EnrolledStudent student;
    private int receiptCounter;

    public Scenario(Fixtures fixtures, Fixtures.Catalog catalog, Timeline timeline, JdbcTemplate jdbc, MutableClock clock, PreviewCashPaymentUseCase previewCashPayment,
                    RegisterCashPaymentUseCase registerCashPayment, SimulationInvariantsFactory invariantsFactory) {
        this.fixtures = fixtures;
        this.catalog = catalog;
        this.timeline = timeline;
        this.jdbc = jdbc;
        this.clock = clock;
        this.previewCashPayment = previewCashPayment;
        this.registerCashPayment = registerCashPayment;
        this.invariants = invariantsFactory.create(this::renderLedger);
        timeline.onEachFiring(firing -> invariants.assertHoldAfter(firing.label()));
    }

    @FunctionalInterface
    public interface SimulationInvariantsFactory {
        SimulationInvariants create(java.util.function.Supplier<String> ledgerTable);
    }

    public Scenario enrollStudent(LocalDate on) {
        timeline.advanceTo(on);
        student = fixtures.enrollStudent(catalog, on);
        invariants.assertHoldAfter("enrollStudent " + on);
        return this;
    }

    public Scenario advanceTo(LocalDate day) {
        timeline.advanceTo(day);
        invariants.assertHoldAfter("advanceTo " + day);
        return this;
    }

    public Scenario advanceDayByDay(LocalDate from, LocalDate to) {
        timeline.advanceDayByDay(from, to);
        return this;
    }

    public Scenario payEverythingDue() {
        Money amount = ars(ledger().outstandingTotal());
        actAsDirector();
        try {
            CashPaymentPreview preview = previewCashPayment.previewCashPayment(student.studentId(), amount);
            receiptCounter++;
            registerCashPayment.registerCashPayment(student.studentId(),
                    new RegisterCashPaymentRequest(amount, null, "SIM-" + receiptCounter, null, preview.fingerprint()), UUID.randomUUID().toString());
        } finally {
            SecurityContextHolder.clearContext();
        }
        invariants.assertHoldAfter("payEverythingDue " + amount.amount().toPlainString());
        return this;
    }

    public Ledger ledger() {
        return Ledger.ofStudent(jdbc, student.studentId());
    }

    public Long studentId() {
        return student.studentId();
    }

    public Long programId() {
        return catalog.programId();
    }

    public Long directorId() {
        return catalog.directorId();
    }

    public Long enrollmentId() {
        return student.enrollmentId();
    }

    public Scenario assertInvariantsAfter(String label) {
        invariants.assertHoldAfter(label);
        return this;
    }

    public Scenario asDirector(Runnable action) {
        actAsDirector();
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
        return this;
    }

    public Timeline timeline() {
        return timeline;
    }

    public List<String> timelineLog() {
        return timeline.log();
    }

    public Scenario finish() {
        invariants.assertHoldAfter("scenario end");
        invariants.assertNoPendingPublicationsAfter("scenario end");
        return this;
    }

    private String renderLedger() {
        return student == null ? "(no student yet)" : ledger().render();
    }

    private void actAsDirector() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(String.valueOf(catalog.directorId()), null,
                List.of(new SimpleGrantedAuthority("ROLE_DIRECTOR"))));
    }
}
