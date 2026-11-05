package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.WithdrawalReason;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class StudentResponseTest {

    private Student studentWithAvatar(String avatarPath) {
        Person person = new Person();
        person.setAvatarPath(avatarPath);
        Student student = new Student();
        student.setPerson(person);
        student.setWithdrawalReason(WithdrawalReason.ABANDONO);
        student.setRegisteredAt(LocalDateTime.of(2026, 11, 5, 10, 0));
        return student;
    }

    @Test
    void hasAvatarReflectsAvatarPath() {
        assertThat(StudentResponse.from(studentWithAvatar("a/b.png"), false).hasAvatar()).isTrue();
        assertThat(StudentResponse.from(studentWithAvatar(null), false).hasAvatar()).isFalse();
    }

    @Test
    void withdrawalReasonIsHiddenUnlessIncluded() {
        assertThat(StudentResponse.from(studentWithAvatar(null), true).withdrawalReason()).isEqualTo("ABANDONO");
        assertThat(StudentResponse.from(studentWithAvatar(null), false).withdrawalReason()).isNull();
    }

    @Test
    void registeredAtIsCopiedFromTheStudent() {
        assertThat(StudentResponse.from(studentWithAvatar(null), false).registeredAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 5, 10, 0));
    }
}
