package com.red_escuela.backend.config;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestSubjectCodesTest {

    @Test
    void generatesCodesThatFitTheColumn() {
        assertThat(TestSubjectCodes.next()).hasSize(4).matches("[0-9A-Z]{4}");
    }

    @Test
    void neverRepeatsACodeWithinTheSameRun() {
        Set<String> generados = new HashSet<>();
        for (int i = 0; i < 20000; i++) {
            assertThat(generados.add(TestSubjectCodes.next())).isTrue();
        }
    }

    @Test
    void isSafeUnderConcurrentUse() {
        Set<String> generados = ConcurrentHashMap.newKeySet();
        IntStream.range(0, 2000).parallel().forEach(i -> generados.add(TestSubjectCodes.next()));
        assertThat(generados).hasSize(2000);
    }
}
