package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("D-240")
@Tag("D-295")
class AcquireCohortPromotionConcurrencyIT extends AbstractCohortPromotionAcquisitionIT {
    private static final int ROUNDS = 5;
    private static final int TIMEOUT_SECONDS = 60;

    @Test
    void twoEnrollmentsRacingForTheLastPlaceLeaveExactlyOneWinnerAndTheQuotaFull() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Long definitionId = publish(promotion("Último lugar " + round, "10", 1));
                IssuingScenarioFactory.Learner first = newLearner();
                IssuingScenarioFactory.Learner second = newLearner();
                assertThat(placesUsed(definitionId)).as("round %d before", round).isZero();
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);

                List<Future<?>> racers = new ArrayList<>();
                for (IssuingScenarioFactory.Learner learner : List.of(first, second)) {
                    racers.add(pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        acquire(learner);
                        return null;
                    }));
                }
                ready.await();
                go.countDown();
                for (Future<?> racer : racers) {
                    racer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }

                assertThat(placesUsed(definitionId)).as("round %d places used", round).isEqualTo(1);
                assertThat(acquisitionsOf(first).size() + acquisitionsOf(second).size()).as("round %d winners", round).isEqualTo(1);
                assertThat(quotaExhaustionWasNotified(definitionId)).as("round %d notified", round).isTrue();
                await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(quotaExhaustedNoticesFor(definitionId)).hasSize(1));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void manyEnrollmentsRacingForTheSamePromotionNeverExceedTheQuota() throws Exception {
        int contenders = 6;
        int quota = 3;
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        try {
            Long definitionId = publish(promotion("Cupo de tres", "10", quota));
            List<IssuingScenarioFactory.Learner> learners = new ArrayList<>();
            for (int index = 0; index < contenders; index++) {
                learners.add(newLearner());
            }
            CountDownLatch ready = new CountDownLatch(contenders);
            CountDownLatch go = new CountDownLatch(1);

            List<Future<?>> racers = new ArrayList<>();
            for (IssuingScenarioFactory.Learner learner : learners) {
                racers.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    acquire(learner);
                    return null;
                }));
            }
            ready.await();
            go.countDown();
            for (Future<?> racer : racers) {
                racer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }

            assertThat(placesUsed(definitionId)).isEqualTo(quota);
            assertThat(learners.stream().filter(learner -> !acquisitionsOf(learner).isEmpty())).hasSize(quota);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(quotaExhaustedNoticesFor(definitionId)).hasSize(1));
        } finally {
            pool.shutdownNow();
        }
    }
}
