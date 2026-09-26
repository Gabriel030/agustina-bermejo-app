package com.red_escuela.backend;

import com.red_escuela.backend.config.ApplicationTimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {
	public static void main(String[] args) {
		ApplicationTimeZone.applyAsJvmDefault();
		SpringApplication.run(BackendApplication.class, args);
	}
}
