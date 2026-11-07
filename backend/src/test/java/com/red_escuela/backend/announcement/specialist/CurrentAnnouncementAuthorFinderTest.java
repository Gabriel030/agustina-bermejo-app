package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentAnnouncementAuthorTest {

    @Mock
    private CurrentActorFinder currentActorFinder;

    @Test
    void returnsTheCurrentPersonAsAuthor() {
        Person person = new Person();
        person.setId(4L);
        when(currentActorFinder.currentPerson()).thenReturn(person);

        assertThat(new CurrentAnnouncementAuthorFinder(currentActorFinder).currentAuthor()).isSameAs(person);
    }
}
