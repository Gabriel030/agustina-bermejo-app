package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleProposal;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.domain.TeachingGroup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-350")
class CollectionReminderImpactQueryTest {
    private static final LocalDate EFFECTIVE_FROM = LocalDate.of(2027, 3, 4);

    private static ReminderScheduleProposal proposal(CollectionReminderSettings globalSettings) {
        return new ReminderScheduleProposal(new ReminderScheduleTarget(TeachingGroup.PROGRAMS, null), ReminderSchedule.initial(), globalSettings);
    }

    @Test
    void aGlobalSettingsOnlyQueryHasNoScheduleProposal() {
        CollectionReminderSettings settings = CollectionReminderSettings.initial();

        CollectionReminderImpactQuery query = CollectionReminderImpactQuery.ofGlobalSettings(settings, EFFECTIVE_FROM);

        assertThat(query.proposedSchedule()).isEmpty();
        assertThat(query.proposedGlobalSettings()).contains(settings);
        assertThat(query.effectiveFrom()).isEqualTo(EFFECTIVE_FROM);
    }

    @Test
    void aScheduleQueryCarriesItsProposalAndTheGlobalSettingsOfTheProposal() {
        CollectionReminderSettings settings = CollectionReminderSettings.initial();
        ReminderScheduleProposal withSettings = proposal(settings);

        CollectionReminderImpactQuery query = new CollectionReminderImpactQuery(withSettings, EFFECTIVE_FROM);

        assertThat(query.proposedSchedule()).contains(withSettings);
        assertThat(query.proposedGlobalSettings()).contains(settings);
        assertThat(new CollectionReminderImpactQuery(proposal(null), EFFECTIVE_FROM).proposedGlobalSettings()).isEmpty();
    }

    @Test
    void aQueryWithNeitherScheduleNorSettingsIsRefused() {
        assertThatThrownBy(() -> new CollectionReminderImpactQuery(null, null, EFFECTIVE_FROM)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CollectionReminderImpactQuery(proposal(null), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CollectionReminderImpactQuery.ofGlobalSettings(null, EFFECTIVE_FROM)).isInstanceOf(NullPointerException.class);
    }
}
