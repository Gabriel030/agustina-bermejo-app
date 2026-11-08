package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SectionEvaluationConfigReviewerTest {

    @Test
    void stampsStatusReviewerAndTimestamp() {
        CurrentActorFinder finder = mock(CurrentActorFinder.class);
        Person reviewer = new Person();
        when(finder.currentPerson()).thenReturn(reviewer);
        SectionEvaluationConfig config = new SectionEvaluationConfig();

        new SectionEvaluationConfigReviewer(finder)
                .markReviewedByCurrentActor(config, SectionEvaluationConfigStatus.APPROVED);

        assertThat(config.getStatus()).isEqualTo(SectionEvaluationConfigStatus.APPROVED);
        assertThat(config.getReviewedBy()).isSameAs(reviewer);
        assertThat(config.getReviewedAt()).isNotNull();
    }
}
