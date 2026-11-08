package com.red_escuela.backend.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoSeedStepsTest {

    @Test
    void everySeedStepIsRegisteredInRunner() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("demo");
        scanner.setEnvironment(environment);
        scanner.addIncludeFilter(new AssignableTypeFilter(DemoSeedStep.class));
        Set<BeanDefinition> stepClasses = scanner.findCandidateComponents(DemoSeedStep.class.getPackageName());

        assertThat(stepClasses).hasSize(5);
        for (BeanDefinition definition : stepClasses) {
            Class<?> stepClass = Class.forName(definition.getBeanClassName());
            assertThat(stepClass.isAnnotationPresent(Component.class)).isTrue();
            assertThat(stepClass.getAnnotation(Profile.class).value()).containsExactly("demo");
        }
    }

    @Test
    void seedStepsHaveDistinctOrdersAndRunAfterEachOther() {
        List<Integer> orders = List.of(DemoPeopleSeedStep.STEP_ORDER, DemoAcademicOfferSeedStep.STEP_ORDER, DemoEnrollmentSeedStep.STEP_ORDER,
                DemoEvaluationSeedStep.STEP_ORDER, DemoAttendanceSeedStep.STEP_ORDER);

        assertThat(orders).doesNotHaveDuplicates().isSorted();
    }

    @Test
    void ciDatasetHasTwoProgramsOneCourseAndTwelveStudents() {
        DemoDataset dataset = DemoDataset.of(DemoDataset.CI);

        assertThat(dataset.programs()).hasSize(2);
        assertThat(dataset.courses()).hasSize(1);
        assertThat(dataset.studentCount()).isEqualTo(12);
    }

    @Test
    void fullDatasetHasEverySeededProgramAndCourse() {
        DemoDataset dataset = DemoDataset.of(DemoDataset.FULL);

        assertThat(dataset.programs()).hasSize(7);
        assertThat(dataset.courses()).hasSize(7);
    }

    @Test
    void unknownDatasetNameIsRejected() {
        assertThatThrownBy(() -> DemoDataset.of("huge")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void studentUsernamesAreUniqueForTheFullDataset() {
        List<String> usernames = java.util.stream.IntStream.range(0, DemoDataset.of(DemoDataset.FULL).studentCount())
                .mapToObj(DemoPeopleSeedStep::studentUsername).toList();

        assertThat(usernames).doesNotHaveDuplicates();
    }

    @Test
    void subjectCodesAreUniquePerDistinctSubjectName() {
        List<String> codes = DemoCatalog.distinctSubjectNames().stream().map(DemoAcademicOfferSeedStep::subjectCode).toList();

        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    void classDatesAreRecentWeekdaysStrictlyBeforeToday() {
        List<java.time.LocalDate> dates = DemoAttendanceSeedStep.recentWeekdaysBefore(java.time.LocalDate.of(2026, 11, 9), 4);

        assertThat(dates).containsExactly(java.time.LocalDate.of(2026, 11, 3), java.time.LocalDate.of(2026, 11, 4),
                java.time.LocalDate.of(2026, 11, 5), java.time.LocalDate.of(2026, 11, 6));
    }

    @Test
    void academicProfilesCycleThroughEveryProfile() {
        assertThat(java.util.stream.IntStream.range(0, 4).mapToObj(position -> DemoAcademicProfile.of(position, 0)).distinct())
                .hasSize(DemoAcademicProfile.values().length);
    }
}
