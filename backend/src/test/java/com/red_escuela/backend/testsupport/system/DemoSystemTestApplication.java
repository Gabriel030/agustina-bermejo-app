package com.red_escuela.backend.testsupport.system;

import com.red_escuela.backend.BackendApplication;
import org.springframework.boot.SpringApplication;
import org.testcontainers.containers.PostgreSQLContainer;

public final class DemoSystemTestApplication {

    private static final String DEMO_PROFILES_ARGUMENT = "--spring.profiles.active=local,demo";

    private DemoSystemTestApplication() {
    }

    public static void main(String[] args) {
        System.setProperty("spring.devtools.restart.enabled", "false");
        PostgreSQLContainer<?> postgres = SystemTestApplication.startPostgres();
        Runtime.getRuntime().addShutdownHook(new Thread(postgres::stop));
        SpringApplication.from(BackendApplication::main)
                .run(SystemTestApplication.systemTestArguments(postgres, new String[] {DEMO_PROFILES_ARGUMENT}));
    }
}
