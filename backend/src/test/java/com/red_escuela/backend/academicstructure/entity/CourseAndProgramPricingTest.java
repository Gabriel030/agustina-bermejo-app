package com.red_escuela.backend.academicstructure.entity;

import com.red_escuela.backend.academicstructure.exception.CurricularYearOutOfRangeException;
import com.red_escuela.backend.academicstructure.exception.InvalidRegistrationWindowException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseAndProgramPricingTest {

    @Test
    void courseWithoutPriceAcceptsAFirstPrice() {
        Course course = Course.builder().name("Curso").build();

        assertThat(course.changePrice(BigDecimal.valueOf(18000))).isTrue();
        assertThat(course.getPrice()).isEqualByComparingTo("18000");
    }

    @Test
    void courseSamePriceIsNotAChange() {
        Course course = Course.builder().name("Curso").price(new BigDecimal("18000.00")).build();

        assertThat(course.changePrice(BigDecimal.valueOf(18000))).isFalse();
        assertThat(course.changePrice(null)).isFalse();
    }

    @Test
    void courseRegistrationClosingBeforeOpeningIsRejected() {
        Course course = Course.builder()
                .registrationStart(LocalDateTime.of(2027, 2, 10, 0, 0))
                .registrationEnd(LocalDateTime.of(2027, 1, 10, 0, 0))
                .build();

        assertThatThrownBy(course::requireValidRegistrationWindow).isInstanceOf(InvalidRegistrationWindowException.class);
    }

    @Test
    void courseWithOpenEndedOrOrderedRegistrationIsValid() {
        Course openEnded = Course.builder().registrationStart(LocalDateTime.of(2027, 2, 10, 0, 0)).build();
        Course ordered = Course.builder()
                .registrationStart(LocalDateTime.of(2027, 1, 10, 0, 0))
                .registrationEnd(LocalDateTime.of(2027, 2, 10, 0, 0))
                .build();

        assertThatCode(openEnded::requireValidRegistrationWindow).doesNotThrowAnyException();
        assertThatCode(ordered::requireValidRegistrationWindow).doesNotThrowAnyException();
    }

    @Test
    void programWithoutFeesAcceptsFirstRecurringAndEnrollmentFee() {
        Program program = Program.builder().name("Carrera").totalYears(3).build();

        assertThat(program.changeRecurringFee(BigDecimal.valueOf(45000))).isTrue();
        assertThat(program.changeEnrollmentFee(BigDecimal.valueOf(30000))).isTrue();
        assertThat(program.changeRecurringFee(BigDecimal.valueOf(45000))).isFalse();
    }

    @Test
    void curricularYearMustFallWithinProgramDuration() {
        Program program = Program.builder().name("Carrera").totalYears(3).build();

        assertThatThrownBy(() -> program.requireCurricularYearWithinDuration(5)).isInstanceOf(CurricularYearOutOfRangeException.class);
        assertThatThrownBy(() -> program.requireCurricularYearWithinDuration(0)).isInstanceOf(CurricularYearOutOfRangeException.class);
        assertThatCode(() -> program.requireCurricularYearWithinDuration(3)).doesNotThrowAnyException();
        assertThatCode(() -> program.requireCurricularYearWithinDuration(null)).doesNotThrowAnyException();
    }
}
