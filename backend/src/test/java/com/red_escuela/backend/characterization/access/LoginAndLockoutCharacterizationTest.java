package com.red_escuela.backend.characterization.access;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginAndLockoutCharacterizationTest extends AccessCharacterizationSupport {

    @Test
    void loginAcceptsUsernameEmailOrDniAndRecordsSuccessfulAttempts() throws Exception {
        Person person = persistPerson("access.login1", "70000001", "ALUMNO");

        for (String identifier : new String[]{"access.login1", "access.login1@example.com", "70000001"}) {
            login(identifier, PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.expiresIn").isNumber());
        }

        assertThat(countRows("LoginAttempt", "e.person.id = " + person.getId() + " and e.successful = true"))
                .isEqualTo(3);
    }

    @Test
    void loginWithWrongPasswordReturns401CountsFailureAndRecordsAttempt() throws Exception {
        Person person = persistPerson("access.login2", "70000002", "ALUMNO");

        login("access.login2", "Incorrecta1").andExpect(status().isUnauthorized());
        login("access.login2", "Incorrecta1").andExpect(status().isUnauthorized());

        entityManager.flush();
        entityManager.refresh(person);
        assertThat(person.getFailedLoginAttempts()).isEqualTo(2);
        assertThat(person.getLockedUntil()).isNull();
        assertThat(countRows("LoginAttempt", "e.person.id = " + person.getId() + " and e.successful = false"))
                .isEqualTo(2);
    }

    @Test
    void loginWithUnknownUserReturns401AndRecordsAttemptWithoutPerson() throws Exception {
        login("nobody.here", PASSWORD).andExpect(status().isUnauthorized());

        assertThat(countRows("LoginAttempt",
                "e.attemptedUsername = 'nobody.here' and e.person is null and e.successful = false")).isEqualTo(1);
    }

    @Test
    void successfulLoginResetsTheFailedAttemptCounter() throws Exception {
        Person person = persistPerson("access.login3", "70000003", "ALUMNO");

        login("access.login3", "Incorrecta1").andExpect(status().isUnauthorized());
        login("access.login3", "Incorrecta1").andExpect(status().isUnauthorized());
        login("access.login3", PASSWORD).andExpect(status().isOk());

        entityManager.flush();
        entityManager.refresh(person);
        assertThat(person.getFailedLoginAttempts()).isZero();
    }

    @Test
    void fifthConsecutiveFailureLocksTheAccountAndEvenTheCorrectPasswordReturns401() throws Exception {
        Person person = persistPerson("access.lock1", "70000004", "ALUMNO");

        for (int i = 0; i < 5; i++) {
            login("access.lock1", "Incorrecta1").andExpect(status().isUnauthorized());
        }

        entityManager.flush();
        entityManager.refresh(person);
        assertThat(person.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(10));
        assertThat(person.getFailedLoginAttempts()).isZero();

        login("access.lock1", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void lockedAccountAcceptsTheCorrectPasswordOnceTheLockExpires() throws Exception {
        Person person = persistPerson("access.lock2", "70000005", "ALUMNO");
        person.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        entityManager.flush();

        login("access.lock2", PASSWORD).andExpect(status().isOk());

        entityManager.flush();
        entityManager.refresh(person);
        assertThat(person.getLockedUntil()).isNull();
    }

    @Test
    void inactiveUserCannotLoginAndTheFailureDoesNotIncreaseTheCounter() throws Exception {
        Person person = persistPerson("access.inactive", "70000006", "ALUMNO");
        person.setStatus(StatusType.INACTIVE);
        entityManager.flush();

        login("access.inactive", PASSWORD).andExpect(status().isUnauthorized());

        entityManager.flush();
        entityManager.refresh(person);
        assertThat(person.getFailedLoginAttempts()).isZero();
        assertThat(countRows("LoginAttempt", "e.person.id = " + person.getId() + " and e.successful = false"))
                .isEqualTo(1);
    }

    @Test
    void userWithoutPasswordCannotLogin() throws Exception {
        Person person = persistPerson("access.nopass", "70000007", "ALUMNO");
        person.setPassword(null);
        entityManager.flush();

        login("access.nopass", PASSWORD).andExpect(status().isUnauthorized());
    }
}
