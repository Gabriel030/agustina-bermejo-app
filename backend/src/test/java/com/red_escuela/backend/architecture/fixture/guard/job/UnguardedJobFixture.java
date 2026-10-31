package com.red_escuela.backend.architecture.fixture.guard.job;

import org.springframework.scheduling.annotation.Scheduled;

public class UnguardedJobFixture {

    @Scheduled(fixedDelay = 1000)
    public void run() {
    }
}
