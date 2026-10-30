package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseCatalogFinderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 10, 12, 0);

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseSubjectRepository courseSubjectRepository;
    @Mock
    private CourseSubjectSectionRepository courseSectionRepository;
    @InjectMocks
    private CourseCatalogFinder finder;

    private Course course(Long id, Integer capacity, LocalDateTime registrationStart, LocalDateTime registrationEnd) {
        Term term = new Term();
        term.setId(5L);
        term.setStartDate(NOW.plusDays(30));
        Course course = new Course();
        course.setId(id);
        course.setTerm(term);
        course.setCapacity(capacity);
        course.setRegistrationStart(registrationStart);
        course.setRegistrationEnd(registrationEnd);
        return course;
    }

    @Test
    void registrationInsideTheWindowIsOpenAndCarriesTheTermId() {
        when(courseRepository.findById(3L)).thenReturn(Optional.of(course(3L, null, NOW.minusDays(1), NOW.plusDays(1))));

        assertThat(finder.findRegistration(3L, NOW)).isEqualTo(new CourseRegistrationView(3L, 5L, false, true));
    }

    @Test
    void registrationAfterTheWindowIsClosed() {
        when(courseRepository.findById(3L)).thenReturn(Optional.of(course(3L, null, NOW.minusDays(5), NOW.minusDays(1))));

        assertThat(finder.findRegistration(3L, NOW).open()).isFalse();
    }

    @Test
    void invertedRegistrationWindowIsFlagged() {
        when(courseRepository.findById(3L)).thenReturn(Optional.of(course(3L, null, NOW.plusDays(2), NOW.minusDays(2))));

        assertThat(finder.findRegistration(3L, NOW).windowInverted()).isTrue();
    }

    @Test
    void capacityIsExhaustedWhenOccupiedSeatsReachIt() {
        when(courseRepository.findById(3L)).thenReturn(Optional.of(course(3L, 2, null, null)));

        assertThat(finder.hasCapacityFor(3L, 1)).isTrue();
        assertThat(finder.hasCapacityFor(3L, 2)).isFalse();
    }

    @Test
    void unlimitedCapacityAlwaysHasRoom() {
        when(courseRepository.findById(3L)).thenReturn(Optional.of(course(3L, null, null, null)));

        assertThat(finder.hasCapacityFor(3L, 1000)).isTrue();
    }

    @Test
    void missingCourseFails() {
        when(courseRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.findRegistration(3L, NOW)).isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void courseLockOfMissingCourseFails() {
        when(courseRepository.findByIdForUpdate(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.lockCourse(3L)).isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void onlyCoursesWithAPassedWaitlistDeadlineAreListed() {
        Course expired = course(1L, null, null, null);
        expired.setWaitlistDeadlineOffsetDays(1);
        Course pending = course(2L, null, null, null);
        pending.setWaitlistDeadlineOffsetDays(90);
        when(courseRepository.findByWaitlistDeadlineOffsetDaysIsNotNull()).thenReturn(List.of(expired, pending));

        assertThat(finder.findCourseIdsWithWaitlistDeadlinePassed(NOW.toLocalDate().plusDays(40))).containsExactly(1L);
        assertThat(finder.findCourseIdsWithWaitlistDeadlinePassed(LocalDate.of(2026, 5, 10))).isEmpty();
    }
}
