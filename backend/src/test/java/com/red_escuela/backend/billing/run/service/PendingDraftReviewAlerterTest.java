package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunMode;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
import com.red_escuela.backend.billing.run.repository.DraftInvoiceLineRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-452")
class PendingDraftReviewAlerterTest {
    private static final LocalDate PERIOD = LocalDate.of(2043, 3, 1);
    private static final LocalDate DAY_FIVE = LocalDate.of(2043, 3, 5);

    private final MonthlyInvoiceRunRepository runRepository = mock(MonthlyInvoiceRunRepository.class);
    private final DraftInvoiceLineRepository draftRepository = mock(DraftInvoiceLineRepository.class);
    private final BillingRunAlertPublisher publisher = mock(BillingRunAlertPublisher.class);
    private final PendingDraftReviewAlerter alerter = new PendingDraftReviewAlerter(runRepository, draftRepository, mock(BusinessDayCalendar.class), publisher);

    private MonthlyInvoiceRun runWithId(long id) {
        MonthlyInvoiceRun run = mock(MonthlyInvoiceRun.class);
        when(run.getId()).thenReturn(id);
        when(run.getPeriod()).thenReturn(PERIOD);
        return run;
    }

    @Test
    void aFailureAlertingOneRunDoesNotStopTheAlertOfTheOthers() {
        MonthlyInvoiceRun failing = runWithId(1L);
        MonthlyInvoiceRun healthy = runWithId(2L);
        when(runRepository.findByModeAndStatusOrderByIdAsc(MonthlyInvoiceRunMode.REAL, MonthlyInvoiceRunStatus.AWAITING_REVIEW)).thenReturn(List.of(failing, healthy));
        when(draftRepository.countDraftInvoicesOfRun(anyLong())).thenReturn(4L);
        doThrow(new IllegalStateException("boom")).when(publisher).publishDraftsAwaitingReviewAlert(org.mockito.ArgumentMatchers.eq(1L), any(), any(), anyLong());

        int alerted = alerter.alertRunsWithDraftsStillAwaitingReview(DAY_FIVE);

        assertThat(alerted).isEqualTo(1);
        verify(publisher).publishDraftsAwaitingReviewAlert(2L, PERIOD, DAY_FIVE, 4L);
    }
}
