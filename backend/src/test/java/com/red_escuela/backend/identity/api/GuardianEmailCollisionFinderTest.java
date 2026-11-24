package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuardianEmailCollisionFinderTest {

    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final GuardianEmailCollisionFinder finder = new GuardianEmailCollisionFinder(studentRepository);

    @Test
    void emptyInputDoesNotQueryTheRepository() {
        assertThat(finder.findPersonIdsWithEmailSharedWithGuardian(List.of())).isEmpty();

        verify(studentRepository, never()).findPersonIdsWithEmailEqualToAnyGuardianEmail(any());
    }

    @Test
    void queriesInChunksOfFiveHundredAndMergesResults() {
        List<Long> ids = LongStream.rangeClosed(1, 1200).boxed().toList();
        when(studentRepository.findPersonIdsWithEmailEqualToAnyGuardianEmail(any())).thenReturn(List.of(7L)).thenReturn(List.of(600L)).thenReturn(List.of(1100L));

        assertThat(finder.findPersonIdsWithEmailSharedWithGuardian(ids)).containsExactlyInAnyOrder(7L, 600L, 1100L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> chunks = ArgumentCaptor.forClass(Collection.class);
        verify(studentRepository, times(3)).findPersonIdsWithEmailEqualToAnyGuardianEmail(chunks.capture());
        assertThat(chunks.getAllValues()).extracting(Collection::size).containsExactly(500, 500, 200);
    }

    @Test
    void duplicatedIdsAreQueriedOnce() {
        when(studentRepository.findPersonIdsWithEmailEqualToAnyGuardianEmail(any())).thenReturn(List.of(1L));

        assertThat(finder.findPersonIdsWithEmailSharedWithGuardian(List.of(1L, 1L, 1L))).containsExactly(1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> chunk = ArgumentCaptor.forClass(Collection.class);
        verify(studentRepository).findPersonIdsWithEmailEqualToAnyGuardianEmail(chunk.capture());
        assertThat(chunk.getValue()).containsExactly(1L);
    }
}
