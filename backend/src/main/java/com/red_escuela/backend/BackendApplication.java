package com.red_escuela.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling: sin esto ningún @Scheduled dispara, Spring lo ignora en silencio —
// usado por InvoiceGenerationService y el resto de los jobs de Fase 6.
@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
