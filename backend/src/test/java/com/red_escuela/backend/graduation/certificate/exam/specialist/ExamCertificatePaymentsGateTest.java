package com.red_escuela.backend.graduation.certificate.exam.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.evaluation.api.PaymentsStanding;
import com.red_escuela.backend.evaluation.api.StudentPaymentsStandingPort;
import com.red_escuela.backend.graduation.debtexception.exception.DocumentRequiresPaymentsUpToDateException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExamCertificatePaymentsGateTest {

    private final StudentPaymentsStandingPort port = mock(StudentPaymentsStandingPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);
    private final ExamCertificatePaymentsGate gate = new ExamCertificatePaymentsGate(port, clock);

    @Test
    void onlyNotOkStudentsAreReportedAsDebtors() {
        when(port.findPaymentsStanding(any(), any())).thenReturn(Map.of(
                1L, PaymentsStanding.OK, 2L, PaymentsStanding.NOT_OK, 3L, PaymentsStanding.NOT_VERIFIED));

        assertThat(gate.studentsWithDebt(List.of(1L, 2L, 3L))).containsExactly(2L);
    }

    @Test
    void billingOffMeansNotVerifiedAndNoDebt() {
        when(port.findPaymentsStanding(any(), any())).thenReturn(Map.of(7L, PaymentsStanding.NOT_VERIFIED));

        assertThat(gate.studentsWithDebt(List.of(7L))).isEmpty();
        assertThatCode(() -> gate.requireUpToDate(7L)).doesNotThrowAnyException();
    }

    @Test
    void emptyStudentListDoesNotQueryThePort() {
        assertThat(gate.studentsWithDebt(List.of())).isEmpty();

        verifyNoInteractions(port);
    }

    @Test
    void standingIsEvaluatedAtTheClockDate() {
        when(port.findPaymentsStanding(any(), any())).thenReturn(Map.of());

        gate.studentsWithDebt(List.of(1L));

        org.mockito.Mockito.verify(port).findPaymentsStanding(List.of(1L), LocalDate.of(2026, 11, 5));
    }

    @Test
    void requireUpToDateThrowsForDebtor() {
        when(port.findPaymentsStanding(any(), any())).thenReturn(Map.of(9L, PaymentsStanding.NOT_OK));

        assertThatThrownBy(() -> gate.requireUpToDate(9L)).isInstanceOf(DocumentRequiresPaymentsUpToDateException.class);
    }
}
