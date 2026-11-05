package com.red_escuela.backend.announcement.service;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementViewerTest {

    @Mock
    private CurrentActorFinder currentActorFinder;

    @Test
    void returnsTheIdOfTheCurrentPerson() {
        Person person = new Person();
        person.setId(11L);
        when(currentActorFinder.currentPerson()).thenReturn(person);

        assertThat(new AnnouncementViewer(currentActorFinder).currentPersonId()).isEqualTo(11L);
    }
}
