package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PasswordResetCodeCheckerSourceTest {

    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final PasswordResetCodeChecker checker = new PasswordResetCodeChecker(
            mock(PersonRepository.class), tokens, mock(ApplicationEventPublisher.class), Clock.systemUTC(), 10, 30, 50, 24, 60);

    private String sourceHashFor(String ip) {
        Person person = new Person();
        person.setId(1L);
        checker.requireNotLocked(person, ip);
        ArgumentCaptor<String> source = ArgumentCaptor.forClass(String.class);
        verify(tokens, org.mockito.Mockito.atLeastOnce()).countBlockingFailures(eq(1L), source.capture(), anyInt(), any());
        return source.getValue();
    }

    @Test
    void ipv6AddressesOfTheSameSlash64ShareTheSource() {
        assertThat(sourceHashFor("2001:db8:aaaa:bbbb:1::1")).isEqualTo(sourceHashFor("2001:db8:aaaa:bbbb:ffff:2:3:4"));
    }

    @Test
    void ipv6AddressesOfDifferentSlash64sHaveDifferentSources() {
        assertThat(sourceHashFor("2001:db8:aaaa:bbbb::1")).isNotEqualTo(sourceHashFor("2001:db8:aaaa:bbbc::1"));
    }

    @Test
    void ipv4AddressesKeepTheirOwnSource() {
        assertThat(sourceHashFor("10.0.0.1")).isNotEqualTo(sourceHashFor("10.0.0.2"));
    }
}
