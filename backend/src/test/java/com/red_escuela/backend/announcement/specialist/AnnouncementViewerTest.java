package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.api.StudentFinder;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementViewerTest {

    @Mock
    private CurrentActorFinder currentActorFinder;

    @Mock
    private StudentFinder studentFinder;

    @Test
    void returnsTheIdOfTheCurrentPerson() {
        Person person = new Person();
        person.setId(11L);
        when(currentActorFinder.currentPerson()).thenReturn(person);

        assertThat(new AnnouncementViewer(currentActorFinder, studentFinder).currentPersonId()).isEqualTo(11L);
    }

    @Test
    void returnsTheStudentIdLinkedToTheCurrentPerson() {
        Person person = new Person();
        person.setId(11L);
        when(currentActorFinder.currentPerson()).thenReturn(person);
        when(studentFinder.findIdByPersonId(11L)).thenReturn(Optional.of(5L));

        assertThat(new AnnouncementViewer(currentActorFinder, studentFinder).currentStudentId()).contains(5L);
    }

    @Test
    void returnsNoStudentIdWhenThePersonIsNotAStudent() {
        Person person = new Person();
        person.setId(11L);
        when(currentActorFinder.currentPerson()).thenReturn(person);
        when(studentFinder.findIdByPersonId(11L)).thenReturn(Optional.empty());

        assertThat(new AnnouncementViewer(currentActorFinder, studentFinder).currentStudentId()).isEmpty();
    }
}
