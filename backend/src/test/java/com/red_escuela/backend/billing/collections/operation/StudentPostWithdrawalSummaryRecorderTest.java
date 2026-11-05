package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.NonBusinessDays;
import com.red_escuela.backend.billing.collections.domain.PostWithdrawalSummaryCandidate;
import com.red_escuela.backend.billing.collections.domain.WithdrawnEnrollmentDebtLine;
import com.red_escuela.backend.billing.collections.repository.PostWithdrawalSummaryStore;
import com.red_escuela.backend.billing.events.PlannedReminderInvoiceKind;
import com.red_escuela.backend.billing.events.PostWithdrawalSummaryPlannedEvent;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static com.red_escuela.backend.shared.domain.TestMoney.usd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StudentPostWithdrawalSummaryRecorderTest {
    private static final LocalDate JULY_THIRTY_FIRST = LocalDate.of(2027, 7, 31);
    private static final LocalDate AUGUST_SECOND = LocalDate.of(2027, 8, 2);

    private final PostWithdrawalSummaryStore store = mock(PostWithdrawalSummaryStore.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final StudentPostWithdrawalSummaryRecorder recorder = new StudentPostWithdrawalSummaryRecorder(store, publisher);

    private static WithdrawnEnrollmentDebtLine courseLine(Long courseEnrollmentId, Long invoiceId, boolean fee, Money balance) {
        return new WithdrawnEnrollmentDebtLine(7L, 70L, null, courseEnrollmentId, JULY_THIRTY_FIRST, invoiceId, fee, LocalDate.of(2027, 7, 10), balance, "Curso de Cocina");
    }

    @Test
    @Tag("D-338")
    void oneEventPerStudentCarriesEveryWithdrawnEnrollmentGroupedByCurrencyAndEachEnrollmentIsRecorded() {
        WithdrawnEnrollmentDebtLine programLine = new WithdrawnEnrollmentDebtLine(7L, 70L, 5L, null, JULY_THIRTY_FIRST, 100L, false, LocalDate.of(2027, 7, 10), ars(1000), "Tecnicatura en Enfermería");
        PostWithdrawalSummaryCandidate candidate = new PostWithdrawalSummaryCandidate(7L, 70L, List.of(programLine, courseLine(9L, 101L, true, usd(20)),
                courseLine(9L, 102L, false, ars(500))));

        recorder.recordSummaryOf(candidate, AUGUST_SECOND);

        verify(store).recordSummaryOf(7L, 5L, null, AUGUST_SECOND);
        verify(store).recordSummaryOf(7L, null, 9L, AUGUST_SECOND);
        ArgumentCaptor<PostWithdrawalSummaryPlannedEvent> captor = ArgumentCaptor.forClass(PostWithdrawalSummaryPlannedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        PostWithdrawalSummaryPlannedEvent event = captor.getValue();
        assertThat(event.studentPersonId()).isEqualTo(70L);
        assertThat(event.businessDate()).isEqualTo(AUGUST_SECOND);
        assertThat(event.blocks()).hasSize(2);
        assertThat(event.blocks().get(0).total()).isEqualTo(ars(1500));
        assertThat(event.blocks().get(0).lines()).hasSize(2);
        assertThat(event.blocks().get(1).total()).isEqualTo(usd(20));
        assertThat(event.blocks().get(1).lines().get(0).invoiceKind()).isEqualTo(PlannedReminderInvoiceKind.ENROLLMENT_FEE);
        assertThat(event.blocks().get(0).total().currency()).isEqualTo(Currency.ARS);
    }

    @Test
    @Tag("D-338")
    void eachLineOfTheEventCarriesTheNameAndTheWithdrawalDateOfItsEnrollment() {
        WithdrawnEnrollmentDebtLine programLine = new WithdrawnEnrollmentDebtLine(7L, 70L, 5L, null, LocalDate.of(2027, 7, 15), 100L, false, LocalDate.of(2027, 7, 10), ars(1000),
                "Tecnicatura en Enfermería");
        PostWithdrawalSummaryCandidate candidate = new PostWithdrawalSummaryCandidate(7L, 70L, List.of(programLine, courseLine(9L, 101L, false, ars(500))));

        recorder.recordSummaryOf(candidate, AUGUST_SECOND);

        ArgumentCaptor<PostWithdrawalSummaryPlannedEvent> captor = ArgumentCaptor.forClass(PostWithdrawalSummaryPlannedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        List<PostWithdrawalSummaryPlannedEvent.Line> lines = captor.getValue().blocks().get(0).lines();
        assertThat(lines).extracting(PostWithdrawalSummaryPlannedEvent.Line::withdrawnEnrollmentName).containsExactlyInAnyOrder("Tecnicatura en Enfermería", "Curso de Cocina");
        assertThat(lines).extracting(PostWithdrawalSummaryPlannedEvent.Line::withdrawalDate).containsExactlyInAnyOrder(LocalDate.of(2027, 7, 15), JULY_THIRTY_FIRST);
    }

    @Test
    @Tag("D-338")
    void anInvoiceLinkedToTwoWithdrawnEnrollmentsIsCountedOnce() {
        PostWithdrawalSummaryCandidate candidate = new PostWithdrawalSummaryCandidate(7L, 70L, List.of(courseLine(9L, 100L, false, ars(1000)),
                courseLine(10L, 100L, false, ars(1000))));

        recorder.recordSummaryOf(candidate, AUGUST_SECOND);

        verify(store).recordSummaryOf(eq(7L), eq(null), eq(9L), eq(AUGUST_SECOND));
        verify(store).recordSummaryOf(eq(7L), eq(null), eq(10L), eq(AUGUST_SECOND));
        ArgumentCaptor<PostWithdrawalSummaryPlannedEvent> captor = ArgumentCaptor.forClass(PostWithdrawalSummaryPlannedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().blocks()).hasSize(1);
        assertThat(captor.getValue().blocks().get(0).total()).isEqualTo(ars(1000));
    }

    @Test
    @Tag("D-338")
    void theCandidatesOfTheDayAreGroupedPerStudentAndOnlyIncludeEnrollmentsWhoseSummaryIsDue() {
        WithdrawnEnrollmentDebtLine due = courseLine(9L, 100L, false, ars(1000));
        WithdrawnEnrollmentDebtLine tooOld = new WithdrawnEnrollmentDebtLine(7L, 70L, null, 11L, LocalDate.of(2027, 1, 15), 103L, false, LocalDate.of(2027, 1, 10), ars(300), "Curso de Cocina");
        WithdrawnEnrollmentDebtLine otherStudent = new WithdrawnEnrollmentDebtLine(8L, 80L, null, 12L, JULY_THIRTY_FIRST, 104L, false, LocalDate.of(2027, 7, 10), ars(400), "Curso de Cocina");

        List<PostWithdrawalSummaryCandidate> candidates = PostWithdrawalSummaryCandidate.groupDueOn(List.of(due, tooOld, otherStudent), AUGUST_SECOND, 6,
                NonBusinessDays.weekendsOnly());

        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).lines()).containsExactly(due);
        assertThat(candidates.get(1).studentId()).isEqualTo(8L);
    }
}
