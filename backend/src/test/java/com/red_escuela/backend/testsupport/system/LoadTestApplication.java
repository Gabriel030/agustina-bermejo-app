package com.red_escuela.backend.testsupport.system;

import com.red_escuela.backend.BackendApplication;
import org.springframework.boot.SpringApplication;
import org.testcontainers.containers.PostgreSQLContainer;

public final class LoadTestApplication {

    private LoadTestApplication() {
    }

    public static void main(String[] args) {
        System.setProperty("spring.devtools.restart.enabled", "false");
        PostgreSQLContainer<?> postgres = SystemTestApplication.startPostgres();
        Runtime.getRuntime().addShutdownHook(new Thread(postgres::stop));
        SpringApplication.from(BackendApplication::main)
                .with(LoadTestSeedConfiguration.class)
                .run(SystemTestApplication.systemTestArguments(postgres, args));
    }
}
