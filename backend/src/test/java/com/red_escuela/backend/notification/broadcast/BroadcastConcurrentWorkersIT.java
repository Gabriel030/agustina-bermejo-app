package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.notification.broadcast.specialist.BroadcastEmailDispatchJob;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class BroadcastConcurrentWorkersIT extends AbstractBroadcastDispatchIT {

    @Test
    void twoWorkersRunningAtTheSameTimeNeverSendTheSameMailTwice() throws Exception {
        LocalDate day = LocalDate.of(2045, 3, 10);
        queueBroadcasts(persistPerson(), 120, day);
        BroadcastEmailDispatchJob firstWorker = jobWith(1000, 0, 200, recordingChannel(realGateway));
        BroadcastEmailDispatchJob secondWorker = jobWith(1000, 0, 200, recordingChannel(realGateway));
        at(day, 9, 0);
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> runs = new ArrayList<>();
            for (BroadcastEmailDispatchJob worker : List.of(firstWorker, secondWorker)) {
                runs.add(executor.submit(() -> {
                    start.await();
                    return worker.dispatchPendingBroadcastEmails();
                }));
            }
            for (Future<Integer> run : runs) {
                run.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(sentSubjects).hasSize(120).doesNotHaveDuplicates();
        assertThat(sentOn(day, DeliveryCategory.BROADCAST)).isEqualTo(120);
        assertThat(count("status = 'QUEUED'")).isZero();
        assertThat(count("attempts > 0")).isZero();
    }
}
