package com.red_escuela.backend.graduation.debtexception.specialist;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

class DebtExceptionAccessGuardTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingAuthenticationIsDenied() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> new DebtExceptionAccessGuard().requireNotSubdirector())
                .isInstanceOf(AccessDeniedException.class);
    }
}
