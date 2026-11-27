package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionInstructorFinder;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigApprovedEvent;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigProposedEvent;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigRejectedEvent;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigReminderEvent;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseSectionEvaluationConfigNotificationsTest {

    private SectionEvaluationConfig courseConfig(Term term) {
        CourseSubject courseSubject = new CourseSubject();
        courseSubject.setName("Modulo Curso");
        CourseSubjectSection section = new CourseSubjectSection();
        section.setId(55L);
        section.setName("Comisión Curso");
        section.setCourseSubject(courseSubject);
        section.setTerm(term);
        SectionEvaluationConfig config = new SectionEvaluationConfig();
        config.setId(9L);
        config.setCourseSection(section);
        config.setStatus(SectionEvaluationConfigStatus.DRAFT);
        config.setRejectionReason("Fechas");
        return config;
    }

    @Test
    void eventsCarryTheCourseSubjectAndSectionNames() {
        SectionEvaluationConfig config = courseConfig(new Term());

        var proposed = SectionEvaluationConfigProposedEvent.proposedFor(config);
        var approved = SectionEvaluationConfigApprovedEvent.approvedFor(config, 4L);
        var rejected = SectionEvaluationConfigRejectedEvent.rejectedFor(config, 4L);

        assertThat(proposed.subjectName()).isEqualTo("Modulo Curso");
        assertThat(proposed.sectionName()).isEqualTo("Comisión Curso");
        assertThat(approved.subjectName()).isEqualTo("Modulo Curso");
        assertThat(approved.instructorPersonId()).isEqualTo(4L);
        assertThat(rejected.sectionName()).isEqualTo("Comisión Curso");
        assertThat(rejected.instructorPersonId()).isEqualTo(4L);
    }

    @Test
    void reminderJobNotifiesTheCourseSectionLead() {
        Term term = new Term();
        term.setEnrollmentStart(ApplicationTime.today().plusDays(10).atStartOfDay());
        SectionEvaluationConfig config = courseConfig(term);
        var configRepository = mock(SectionEvaluationConfigRepository.class);
        var instructorFinder = mock(SectionInstructorFinder.class);
        var publisher = mock(ApplicationEventPublisher.class);
        when(configRepository.findByStatusIn(any())).thenReturn(List.of(config));
        when(instructorFinder.findCourseSectionLeadPersonId(55L)).thenReturn(Optional.of(77L));

        new SectionEvaluationConfigReminderJob(configRepository, instructorFinder, publisher).sendReminders();

        var captor = ArgumentCaptor.forClass(SectionEvaluationConfigReminderEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().instructorPersonId()).isEqualTo(77L);
        assertThat(captor.getValue().sectionName()).isEqualTo("Comisión Curso");
        verify(instructorFinder, never()).findProgramSectionLeadPersonId(any(Long.class));
    }

    @Test
    void reminderJobSkipsCourseSectionsWithoutLead() {
        Term term = new Term();
        term.setEnrollmentStart(ApplicationTime.today().plusDays(5).atStartOfDay());
        var configRepository = mock(SectionEvaluationConfigRepository.class);
        var instructorFinder = mock(SectionInstructorFinder.class);
        var publisher = mock(ApplicationEventPublisher.class);
        when(configRepository.findByStatusIn(any())).thenReturn(List.of(courseConfig(term)));
        when(instructorFinder.findCourseSectionLeadPersonId(55L)).thenReturn(Optional.empty());

        new SectionEvaluationConfigReminderJob(configRepository, instructorFinder, publisher).sendReminders();

        verify(publisher, never()).publishEvent(any(Object.class));
    }
}
