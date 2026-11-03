package com.red_escuela.backend.config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ConcurrentRace {

    public record Outcome<T>(T value, Throwable failure) {
        public boolean succeeded() {
            return failure == null;
        }
    }

    private ConcurrentRace() {
    }

    public static <T> List<Outcome<T>> run(List<Callable<T>> contenders) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(contenders.size());
        CountDownLatch ready = new CountDownLatch(contenders.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> contender : contenders) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return contender.call();
            }));
        }
        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        List<Outcome<T>> outcomes = new ArrayList<>();
        try {
            for (Future<T> future : futures) {
                try {
                    outcomes.add(new Outcome<>(future.get(30, TimeUnit.SECONDS), null));
                } catch (ExecutionException e) {
                    outcomes.add(new Outcome<>(null, e.getCause()));
                }
            }
        } finally {
            executor.shutdownNow();
        }
        return outcomes;
    }
}
