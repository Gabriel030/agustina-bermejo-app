package com.red_escuela.backend.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InClauseChunksTest {
    @Test
    void splitsIntoFullChunksAndAShorterLastOne() {
        List<List<Integer>> chunks = InClauseChunks.of(IntStream.range(0, 2_500).boxed().toList());

        assertThat(chunks).extracting(List::size).containsExactly(1_000, 1_000, 500);
        assertThat(chunks.stream().flatMap(List::stream)).containsExactlyElementsOf(IntStream.range(0, 2_500).boxed().toList());
    }

    @Test
    void anExactMultipleLeavesNoEmptyChunk() {
        assertThat(InClauseChunks.of(List.of(1, 2, 3, 4), 2)).containsExactly(List.of(1, 2), List.of(3, 4));
    }

    @Test
    void noValuesProduceNoChunks() {
        assertThat(InClauseChunks.of(List.of())).isEmpty();
    }
}
