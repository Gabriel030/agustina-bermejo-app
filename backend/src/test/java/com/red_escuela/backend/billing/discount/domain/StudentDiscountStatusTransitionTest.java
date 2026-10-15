package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.discount.exception.IllegalStudentDiscountTransitionException;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.statemachine.ExhaustiveTransitionExpectations;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.red_escuela.backend.billing.discount.domain.StudentDiscountEvent.*;
import static com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-284")
@Tag("D-282")
@Tag("D-238")
@ExhaustiveTransitionExpectations(machine = StudentDiscountStateMachine.class)
class StudentDiscountStatusTransitionTest {
    private static final Instant NOW = Instant.parse("2027-03-10T10:00:00Z");
    private static final StatusChangeActor ACTOR = StatusChangeActor.ofPerson(1L);
    private static final Class<IllegalStudentDiscountTransitionException> ILLEGAL = IllegalStudentDiscountTransitionException.class;

    private static final Map<StudentDiscountStatus, Map<StudentDiscountEvent, StudentDiscountStatus>> ALLOWED = Map.of(
            ACTIVE, Map.of(LAST_INSTALLMENT_CONSUMED, EXHAUSTED, CONSUMPTION_RETURNED, ACTIVE, DISCOUNT_REVOKED, REVOKED, ENROLLMENT_ENDED, ENDED),
            EXHAUSTED, Map.of(CONSUMPTION_RETURNED, ACTIVE));
    private static final Map<StudentDiscountEvent, StudentDiscountStatus> INITIAL = Map.of(DISCOUNT_GRANTED, ACTIVE);

    static List<TransitionExpectation> expectedTransitions() {
        List<TransitionExpectation> expectations = new ArrayList<>();
        for (StudentDiscountEvent event : StudentDiscountEvent.values()) {
            expectations.add(INITIAL.containsKey(event) ? TransitionExpectation.allows(null, event, INITIAL.get(event)) : TransitionExpectation.rejects(null, event, ILLEGAL));
        }
        for (StudentDiscountStatus from : StudentDiscountStatus.values()) {
            for (StudentDiscountEvent event : StudentDiscountEvent.values()) {
                StudentDiscountStatus target = ALLOWED.getOrDefault(from, Map.of()).get(event);
                expectations.add(target != null ? TransitionExpectation.allows(from, event, target) : TransitionExpectation.rejects(from, event, ILLEGAL));
            }
        }
        return expectations;
    }

    static Stream<TransitionExpectation> everyPair() {
        return expectedTransitions().stream();
    }

    private static StudentDiscount grantedDiscount() {
        StudentDiscountGrant grant = new StudentDiscountGrant(EnrollmentReference.ofProgram(10L), 20L, Currency.ARS, 1L, DiscountType.SCHOLARSHIP,
                InvoiceType.MONTHLY_INSTALLMENT, new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("50"))), null, 1,
                new ScholarshipPeriodRange(YearMonth.of(2027, 4), YearMonth.of(2027, 8)), "motivo");
        return StudentDiscount.grantStudentDiscount(grant, 1L, null, NOW);
    }

    private static StudentDiscount discountIn(StudentDiscountStatus status) {
        StudentDiscount discount = grantedDiscount();
        switch (status) {
            case EXHAUSTED -> discount.recordLastInstallmentConsumed(ACTOR, NOW);
            case REVOKED -> discount.revokeStudentDiscount("motivo", YearMonth.of(2027, 4), ACTOR, NOW);
            case ENDED -> discount.endWithEnrollment(ACTOR, NOW);
            default -> {
            }
        }
        return discount;
    }

    private static void apply(StudentDiscount discount, StudentDiscountEvent event) {
        ReflectionTestUtils.invokeMethod(discount, "moveTo", event, ACTOR, NOW, "x");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyPair")
    void everyStateAndEventPairBehavesAsTheTableSays(TransitionExpectation expectation) {
        StudentDiscountStatus from = (StudentDiscountStatus) expectation.fromState();
        StudentDiscountEvent event = (StudentDiscountEvent) expectation.event();
        if (from == null) {
            assertThat(StudentDiscountStateMachine.findTargetStateFor(null, event)).isEqualTo(Optional.ofNullable((StudentDiscountStatus) expectation.toState()));
            return;
        }
        StudentDiscount discount = discountIn(from);
        int changes = discount.getStatusChanges().size();
        if (expectation.isAllowed()) {
            apply(discount, event);
            assertThat(discount.getStatus()).isEqualTo(expectation.toState());
            assertThat(discount.getStatusChanges()).hasSize(changes + 1);
            StudentDiscountStatusChange last = discount.getStatusChanges().get(changes);
            assertThat(last.getFromStatus()).isEqualTo(from);
            assertThat(last.getToStatus()).isEqualTo(expectation.toState());
            assertThat(last.getEvent()).isEqualTo(event);
        } else {
            assertThatThrownBy(() -> apply(discount, event)).isInstanceOf(expectation.rejection());
            assertThat(discount.getStatus()).isEqualTo(from);
            assertThat(discount.getStatusChanges()).hasSize(changes);
        }
    }

    @Test
    void theTableListsExactlyTheSixAllowedTransitions() {
        assertThat(StudentDiscountStateMachine.TRANSITIONS.listAllowedTransitions()).hasSize(6);
    }

    @Test
    void grantingStartsActiveWithAHistoryRowAndRevokingRecordsTheFirstAffectedPeriod() {
        StudentDiscount discount = grantedDiscount();

        assertThat(discount.getStatus()).isEqualTo(ACTIVE);
        assertThat(discount.getStatusChanges()).hasSize(1);
        discount.revokeStudentDiscount("cambió la situación", YearMonth.of(2027, 7), ACTOR, NOW);

        assertThat(discount.getStatus()).isEqualTo(REVOKED);
        assertThat(discount.getRevokedFromPeriod()).isEqualTo(YearMonth.of(2027, 7));
        assertThat(discount.getRevocationReason()).isEqualTo("cambió la situación");
        assertThat(discount.coversPeriod(YearMonth.of(2027, 6))).isTrue();
        assertThat(discount.coversPeriod(YearMonth.of(2027, 7))).isFalse();
        assertThat(discount.coversPeriod(YearMonth.of(2027, 3))).isFalse();
    }

    @Test
    void anEndedDiscountCoversNoPeriod() {
        StudentDiscount discount = grantedDiscount();
        discount.endWithEnrollment(ACTOR, NOW);

        assertThat(discount.coversPeriod(YearMonth.of(2027, 5))).isFalse();
    }
}
