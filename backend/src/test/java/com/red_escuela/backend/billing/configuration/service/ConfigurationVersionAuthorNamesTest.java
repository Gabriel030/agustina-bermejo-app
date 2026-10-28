package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.identity.api.PersonNameLookup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-351")
class ConfigurationVersionAuthorNamesTest {
    private final PersonNameLookup personNames = mock(PersonNameLookup.class);
    private final ConfigurationVersionAuthorNames service = new ConfigurationVersionAuthorNames(personNames);

    private static ConfigurationVersion versionBy(Long proposedById, Long decidedById) {
        ConfigurationVersion version = mock(ConfigurationVersion.class);
        when(version.getProposedById()).thenReturn(proposedById);
        when(version.getDecidedById()).thenReturn(decidedById);
        return version;
    }

    @Test
    void itResolvesTheProposersAndTheDecidersOfAllTheVersionsInOneLookupWithoutNullsOrRepeats() {
        when(personNames.findFullNamesByPersonId(anyCollection())).thenReturn(Map.of(1L, "Ana Proponente", 2L, "Dario Decisor"));

        Map<Long, String> names = service.findNamesOf(List.of(versionBy(1L, 2L), versionBy(1L, null), versionBy(2L, 2L)));

        assertThat(names).containsEntry(1L, "Ana Proponente").containsEntry(2L, "Dario Decisor");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(personNames).findFullNamesByPersonId(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrderElementsOf(Set.of(1L, 2L)).hasSize(2);
    }
}
