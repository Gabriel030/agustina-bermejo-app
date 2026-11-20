package com.red_escuela.backend.shared.audit;

import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditTrailTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T15:30:00Z"), ZoneOffset.UTC);

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditTrail auditTrail = new AuditTrail(repository, CLOCK);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private AuditLogEntry savedEntry() {
        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private static AuditRecord withdrawal() {
        return new AuditRecord(AuditAction.STAFF_WITHDRAWN, AuditEntityType.PERSON, 60L, "ACTIVE", "INACTIVE", "motivo");
    }

    @Test
    void recordStoresActorRoleActionEntityValuesReasonAndInjectedClockInstant() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "42", null, AuthorityUtils.createAuthorityList("ROLE_DIRECTOR")));

        auditTrail.record(withdrawal());

        AuditLogEntry entry = savedEntry();
        assertThat(entry.getActorPersonId()).isEqualTo(42L);
        assertThat(entry.getActorRole()).isEqualTo("DIRECTOR");
        assertThat(entry.getAction()).isEqualTo(AuditAction.STAFF_WITHDRAWN);
        assertThat(entry.getEntityType()).isEqualTo(AuditEntityType.PERSON);
        assertThat(entry.getEntityId()).isEqualTo(60L);
        assertThat(entry.getBeforeValue()).isEqualTo("ACTIVE");
        assertThat(entry.getAfterValue()).isEqualTo("INACTIVE");
        assertThat(entry.getReason()).isEqualTo("motivo");
        assertThat(entry.getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 11, 14, 15, 30));
    }

    @Test
    void occurredAtFollowsTheZoneOfTheInjectedClock() {
        Clock buenosAires = Clock.fixed(Instant.parse("2026-11-14T15:30:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));

        new AuditTrail(repository, buenosAires).record(withdrawal());

        assertThat(savedEntry().getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 11, 14, 12, 30));
    }

    @Test
    void recordWithoutAuthenticationStoresNoActor() {
        auditTrail.record(withdrawal());

        AuditLogEntry entry = savedEntry();
        assertThat(entry.getActorPersonId()).isNull();
        assertThat(entry.getActorRole()).isNull();
    }

    @Test
    void recordWithAnAnonymousAuthenticationStoresNoActor() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        auditTrail.record(withdrawal());

        AuditLogEntry entry = savedEntry();
        assertThat(entry.getActorPersonId()).isNull();
        assertThat(entry.getActorRole()).isNull();
    }

    @Test
    void aNonNumericPrincipalNameStoresNoActorId() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "system", null, AuthorityUtils.createAuthorityList("ROLE_DIRECTOR")));

        auditTrail.record(withdrawal());

        assertThat(savedEntry().getActorPersonId()).isNull();
    }

    @Test
    void withSeveralRolesTheFirstInAlphabeticalOrderIsStored() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "42", null, AuthorityUtils.createAuthorityList("ROLE_DOCENTE", "SCOPE_x", "ROLE_ALUMNO")));

        auditTrail.record(withdrawal());

        assertThat(savedEntry().getActorRole()).isEqualTo("ALUMNO");
    }

    @Test
    void auditRecordRequiresActionEntityTypeAndEntityId() {
        assertThatThrownBy(() -> new AuditRecord(null, AuditEntityType.PERSON, 1L, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuditRecord(AuditAction.STAFF_WITHDRAWN, null, 1L, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuditRecord(AuditAction.STAFF_WITHDRAWN, AuditEntityType.PERSON, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
