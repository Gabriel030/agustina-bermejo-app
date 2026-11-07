package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstitutionNameResolverTest {

    private final InstitutionRepository repository = mock(InstitutionRepository.class);
    private final InstitutionNameResolver resolver = new InstitutionNameResolver(repository);

    @Test
    void returnsDefaultNameWhenNoInstitutionExists() {
        when(repository.findAll()).thenReturn(List.of());

        assertThat(resolver.resolveInstitutionName()).isEqualTo("Red Escuela");
    }

    @Test
    void returnsFirstInstitutionName() {
        Institution first = mock(Institution.class);
        Institution second = mock(Institution.class);
        when(first.getName()).thenReturn("Instituto Norte");
        when(second.getName()).thenReturn("Instituto Sur");
        when(repository.findAll()).thenReturn(List.of(first, second));

        assertThat(resolver.resolveInstitutionName()).isEqualTo("Instituto Norte");
    }
}
