package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProgramApplicationHistoryTest {

    private final ProgramEnrollmentApplicationRepository repository = mock(ProgramEnrollmentApplicationRepository.class);
    private final ProgramApplicationReviewViews views = mock(ProgramApplicationReviewViews.class);
    private final ProgramApplicationHistory history = new ProgramApplicationHistory(repository, views);

    private String[] patternsFor(String search) {
        when(repository.searchByStatuses(anyCollection(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());
        history.searchResolved(null, search, 0, 10);
        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> dniPattern = ArgumentCaptor.forClass(String.class);
        verify(repository).searchByStatuses(anyCollection(), pattern.capture(), dniPattern.capture(), any(Pageable.class));
        return new String[]{pattern.getValue(), dniPattern.getValue()};
    }

    @Test
    void aDniTypedWithDotsMatchesTheStoredDigits() {
        String[] patterns = patternsFor(" 12.345.678 ");

        assertThat(patterns[1]).isEqualTo("%12345678%");
        assertThat(patterns[0]).isEqualTo("%12.345.678%");
    }

    @Test
    void theNameSearchIsLowercasedAndTheWildcardsAreEscaped() {
        String[] patterns = patternsFor("Pérez_50%");

        assertThat(patterns[0]).isEqualTo("%pérez!_50!%%");
    }

    @Test
    void aSearchMadeOnlyOfSeparatorsDoesNotMatchEveryDni() {
        assertThat(patternsFor("...")[1]).isEqualTo("%...%");
    }

    @Test
    void theSearchTermIsCappedAtOneHundredCharacters() {
        String[] patterns = patternsFor("a".repeat(300));

        assertThat(patterns[0]).isEqualTo("%" + "a".repeat(100) + "%");
    }

    @Test
    void aBlankSearchMatchesEverything() {
        String[] patterns = patternsFor("   ");

        assertThat(patterns).containsExactly("%", "%");
    }
}
