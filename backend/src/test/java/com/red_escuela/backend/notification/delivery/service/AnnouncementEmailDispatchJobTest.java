package com.red_escuela.backend.notification.delivery.service;

import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnnouncementEmailDispatchJobTest {
    private static final Clock SATURDAY_NIGHT = Clock.fixed(Instant.parse("2027-03-13T22:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate SATURDAY = LocalDate.of(2027, 3, 13);

    private final NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
    private final EmailDeliveryChannel channel = mock(EmailDeliveryChannel.class);
    private final AnnouncementEmailDispatchJob job =
            new AnnouncementEmailDispatchJob(repository, channel, SATURDAY_NIGHT, new SimpleMeterRegistry());

    private void idsScheduledFor(LocalDate day, List<Long> ids) {
        when(repository.findDispatchableIds(eq(DeliveryChannel.EMAIL), eq(DeliveryCategory.ANNOUNCEMENT), anyList(), eq(day), any(), anyLong(), any()))
                .thenReturn(ids);
    }

    @Test
    void dispatchesImmediatelyOutsideCollectionWindow() {
        idsScheduledFor(SATURDAY, List.of(5L, 6L));

        int attempted = job.dispatchPendingAnnouncementEmails();

        assertThat(attempted).isEqualTo(2);
        verify(channel).dispatchDelivery(5L);
        verify(channel).dispatchDelivery(6L);
    }

    @Test
    void picksUpDeliveriesLeftFromYesterday() {
        idsScheduledFor(SATURDAY.minusDays(1), List.of(4L));

        job.dispatchPendingAnnouncementEmails();

        verify(channel).dispatchDelivery(4L);
    }

    @Test
    void aFailingDeliveryDoesNotStopTheOthers() {
        idsScheduledFor(SATURDAY, List.of(5L, 6L));
        doThrow(new IllegalStateException("boom")).when(channel).dispatchDelivery(5L);

        int attempted = job.dispatchPendingAnnouncementEmails();

        assertThat(attempted).isEqualTo(2);
        verify(channel).dispatchDelivery(6L);
    }

    @Test
    void capsTheNumberOfDispatchesPerRun() {
        List<Long> many = LongStream.rangeClosed(1, AnnouncementEmailDispatchJob.MAX_DISPATCHES_PER_RUN).boxed().toList();
        idsScheduledFor(SATURDAY.minusDays(1), many);
        idsScheduledFor(SATURDAY, List.of(9_999L));

        int attempted = job.dispatchPendingAnnouncementEmails();

        assertThat(attempted).isEqualTo(AnnouncementEmailDispatchJob.MAX_DISPATCHES_PER_RUN);
        verify(channel, never()).dispatchDelivery(9_999L);
    }
}
