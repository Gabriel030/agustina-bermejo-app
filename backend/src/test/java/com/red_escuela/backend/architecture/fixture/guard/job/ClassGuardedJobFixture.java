package com.red_escuela.backend.architecture.fixture.guard.job;

import com.red_escuela.backend.shared.module.RequiresModule;
import org.springframework.scheduling.annotation.Scheduled;

@RequiresModule("billing")
public class ClassGuardedJobFixture {

    @Scheduled(fixedDelay = 1000)
    public void run() {
    }
}
