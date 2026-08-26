package com.red_escuela.backend;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

}
