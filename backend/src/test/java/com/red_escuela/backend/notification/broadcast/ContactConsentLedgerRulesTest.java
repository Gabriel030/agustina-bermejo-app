package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.identity.api.NewsletterPreferenceFinder;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.domain.ConsentSource;
import com.red_escuela.backend.notification.broadcast.domain.ConsentStatus;
import com.red_escuela.backend.notification.broadcast.domain.DenialReason;
import com.red_escuela.backend.notification.broadcast.entity.ContactConsent;
import com.red_escuela.backend.notification.broadcast.repository.ContactConsentRepository;
import com.red_escuela.backend.notification.broadcast.specialist.ContactConsentLedger;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContactConsentLedgerRulesTest {

    private static final Long PERSON_ID = 42L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);

    private final List<ContactConsent> stored = new ArrayList<>();
    private final ContactConsentRepository repository = mock(ContactConsentRepository.class);
    private final NewsletterPreferenceFinder newsletterPreferenceFinder = mock(NewsletterPreferenceFinder.class);
    private final AuditTrail auditTrail = mock(AuditTrail.class);
    private ContactConsentLedger ledger;

    @BeforeEach
    void setUp() {
        when(repository.findByPersonIdAndChannelAndCategory(anyLong(), any(), any())).thenAnswer(invocation -> stored.stream()
                .filter(row -> row.getPersonId().equals(invocation.getArgument(0))
                        && row.getChannel() == invocation.getArgument(1)
                        && row.getCategory() == invocation.getArgument(2))
                .findFirst());
        when(repository.save(any(ContactConsent.class))).thenAnswer(invocation -> {
            ContactConsent row = invocation.getArgument(0);
            stored.add(row);
            return row;
        });
        ledger = new ContactConsentLedger(repository, newsletterPreferenceFinder, auditTrail, CLOCK);
    }

    @Test
    void institutionalAllowedWithoutRecord() {
        assertThat(ledger.isAllowed(PERSON_ID, ChannelType.EMAIL, BroadcastCategory.INSTITUTIONAL).allowed()).isTrue();
    }

    @Test
    void whatsAppApiDeniedWithoutRecordInEveryCategory() {
        for (BroadcastCategory category : BroadcastCategory.values()) {
            var decision = ledger.isAllowed(PERSON_ID, ChannelType.WHATSAPP_API, category);

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.denialReason()).isEqualTo(DenialReason.NO_CONSENT);
        }
    }

    @Test
    void whatsAppApiAllowedWithGrantedRecordFromAccountCreation() {
        ledger.recordConsent(PERSON_ID, ConsentChannel.WHATSAPP, BroadcastCategory.INSTITUTIONAL, true, ConsentSource.ACCOUNT_CREATION, null);

        assertThat(ledger.isAllowed(PERSON_ID, ChannelType.WHATSAPP_API, BroadcastCategory.INSTITUTIONAL).allowed()).isTrue();
    }

    @Test
    void institutionalDeniedAfterRevocation() {
        ledger.recordConsent(PERSON_ID, ConsentChannel.EMAIL, BroadcastCategory.INSTITUTIONAL, false, ConsentSource.PROFILE, null);

        var decision = ledger.isAllowed(PERSON_ID, ChannelType.EMAIL, BroadcastCategory.INSTITUTIONAL);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.denialReason()).isEqualTo(DenialReason.OPTED_OUT);
    }

    @Test
    void promotionalDeniedWithoutRecord() {
        var decision = ledger.isAllowed(PERSON_ID, ChannelType.WHATSAPP_LINK, BroadcastCategory.PROMOTIONAL);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.denialReason()).isEqualTo(DenialReason.NO_CONSENT);
    }

    @Test
    void promotionalAllowedAfterGrant() {
        ledger.recordConsent(PERSON_ID, ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL, true, ConsentSource.PROFILE, null);

        assertThat(ledger.isAllowed(PERSON_ID, ChannelType.EMAIL, BroadcastCategory.PROMOTIONAL).allowed()).isTrue();
    }

    @Test
    void promotionalEmailFallsBackToNewsletterOptIn() {
        when(newsletterPreferenceFinder.isOptedIn(PERSON_ID)).thenReturn(true);

        assertThat(ledger.isAllowed(PERSON_ID, ChannelType.EMAIL, BroadcastCategory.PROMOTIONAL).allowed()).isTrue();
        assertThat(ledger.isAllowed(PERSON_ID, ChannelType.WHATSAPP_LINK, BroadcastCategory.PROMOTIONAL).allowed()).isFalse();
    }

    @Test
    void explicitRevocationBeatsNewsletterOptIn() {
        when(newsletterPreferenceFinder.isOptedIn(PERSON_ID)).thenReturn(true);
        ledger.recordConsent(PERSON_ID, ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL, false, ConsentSource.EMAIL_LINK, null);

        assertThat(ledger.isAllowed(PERSON_ID, ChannelType.EMAIL, BroadcastCategory.PROMOTIONAL).allowed()).isFalse();
    }

    @Test
    void whatsappConsentWritesBothChannels() {
        ledger.recordConsent(PERSON_ID, ConsentChannel.WHATSAPP, BroadcastCategory.PROMOTIONAL, true, ConsentSource.PROFILE, null);

        assertThat(stored).extracting(ContactConsent::getChannel)
                .containsExactlyInAnyOrder(ChannelType.WHATSAPP_LINK, ChannelType.WHATSAPP_API);
        assertThat(ledger.isGranted(PERSON_ID, ConsentChannel.WHATSAPP, BroadcastCategory.PROMOTIONAL)).isTrue();
    }

    @Test
    void updatesTheExistingRecordInsteadOfInsertingANewOne() {
        ledger.recordConsent(PERSON_ID, ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL, true, ConsentSource.PROFILE, null);
        ledger.recordConsent(PERSON_ID, ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL, false, ConsentSource.EMAIL_LINK, "link");

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getStatus()).isEqualTo(ConsentStatus.REVOKED);
        assertThat(stored.get(0).getSource()).isEqualTo(ConsentSource.EMAIL_LINK);
    }

    @Test
    void auditsEveryChangeButNotARepeatedDecision() {
        ledger.recordConsent(PERSON_ID, ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL, true, ConsentSource.PROFILE, null);
        ledger.recordConsent(PERSON_ID, ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL, true, ConsentSource.PROFILE, null);
        ledger.recordConsent(PERSON_ID, ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL, false, ConsentSource.PROFILE, null);

        ArgumentCaptor<AuditRecord> audits = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditTrail, times(2)).record(audits.capture());
        assertThat(audits.getAllValues()).extracting(AuditRecord::action)
                .containsExactly(AuditAction.CONTACT_CONSENT_GRANTED, AuditAction.CONTACT_CONSENT_REVOKED);
        assertThat(Optional.ofNullable(audits.getAllValues().get(1).beforeValue())).contains("GRANTED");
    }
}
