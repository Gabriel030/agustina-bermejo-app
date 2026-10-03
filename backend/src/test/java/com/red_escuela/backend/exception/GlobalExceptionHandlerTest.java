package com.red_escuela.backend.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/illegal-state")
        void illegalState() {
            throw new IllegalStateException("Invoice 7 en estado PAID no puede cancelarse");
        }

        @GetMapping("/data-integrity")
        void dataIntegrity() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint uk_person_email");
        }

        @GetMapping("/optimistic-lock")
        void optimisticLock() {
            throw new ObjectOptimisticLockingFailureException("Invoice", 7L);
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void anIllegalStateIsAConflictWithItsCodeInsteadOfAServerError(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/illegal-state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"));

        assertThat(output).contains("Invoice 7 en estado PAID no puede cancelarse");
    }

    @Test
    void aDataIntegrityViolationIsLogged(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/data-integrity")).andExpect(status().isConflict());

        assertThat(output).contains("uk_person_email");
    }

    @Test
    void anOptimisticLockingFailureIsLogged(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/optimistic-lock")).andExpect(status().isConflict());

        assertThat(output).contains("ObjectOptimisticLockingFailureException");
    }
}
