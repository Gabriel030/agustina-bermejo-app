package com.red_escuela.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling: primera vez que el proyecto usa @Scheduled (Épica 8 Fase 2,
// RecurringBillingService) — sin esto el cron nunca dispara, Spring lo ignora en
// silencio.
@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
