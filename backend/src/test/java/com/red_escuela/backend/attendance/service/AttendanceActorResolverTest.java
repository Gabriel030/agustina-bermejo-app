package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttendanceActorResolverTest {

    private final CurrentActorFinder currentActorFinder = mock(CurrentActorFinder.class);
    private final AttendanceActorResolver resolver = new AttendanceActorResolver(currentActorFinder);

    @Test
    void returnsTheCurrentPersonFromTheIdentityApi() {
        Person person = new Person();
        when(currentActorFinder.currentPerson()).thenReturn(person);

        assertThat(resolver.currentActor()).isSameAs(person);
    }
}
