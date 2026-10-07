package com.red_escuela.backend.academicstructure.entity;

import com.red_escuela.backend.academicstructure.exception.IncompleteSubjectException;
import com.red_escuela.backend.academicstructure.exception.SubjectInvalidTransitionException;
import com.red_escuela.backend.academicstructure.exception.SubjectNotAssignableToPlanException;
import com.red_escuela.backend.academicstructure.exception.SubjectNotOpenableForSectionsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectTest {

    private Subject completeDraft() {
        return Subject.builder().name("Anatomía").code("ANAT").weeklyHours(4).gradeMode(GradeMode.NUMERIC).build();
    }

    @Test
    void draftWithoutAnyWeeklyHoursCannotBeSubmitted() {
        Subject subject = Subject.builder().name("Sin horas").code("SINH").gradeMode(GradeMode.NUMERIC).weeklyHours(0).build();

        assertThatThrownBy(subject::submitForApproval).isInstanceOf(IncompleteSubjectException.class);
        assertThat(subject.getStatus()).isEqualTo(SubjectStatus.DRAFT);
    }

    @Test
    void draftWithoutGradeModeCannotBeSubmitted() {
        Subject subject = Subject.builder().name("Sin modalidad").code("SINM").weeklyHoursLab(2).build();

        assertThatThrownBy(subject::submitForApproval).isInstanceOf(IncompleteSubjectException.class);
    }

    @Test
    void labHoursAloneAreEnoughToSubmit() {
        Subject subject = Subject.builder().name("Práctica").code("PRAC").weeklyHoursLab(3).gradeMode(GradeMode.PASS_FAIL).build();

        subject.submitForApproval();

        assertThat(subject.getStatus()).isEqualTo(SubjectStatus.PENDING_APPROVAL);
    }

    @Test
    void rejectionStoresReasonAndResubmissionClearsIt() {
        Subject subject = completeDraft();
        subject.submitForApproval();

        subject.reject("Falta bibliografía");

        assertThat(subject.getStatus()).isEqualTo(SubjectStatus.REJECTED);
        assertThat(subject.getRejectionReason()).isEqualTo("Falta bibliografía");

        subject.submitForApproval();

        assertThat(subject.getStatus()).isEqualTo(SubjectStatus.PENDING_APPROVAL);
        assertThat(subject.getRejectionReason()).isNull();
    }

    @Test
    void onlyPendingSubjectsCanBeRejectedOrApproved() {
        Subject subject = completeDraft();

        assertThatThrownBy(() -> subject.reject("motivo")).isInstanceOf(SubjectInvalidTransitionException.class);
        assertThatThrownBy(subject::approve).isInstanceOf(SubjectInvalidTransitionException.class);
    }

    @Test
    void pendingSubjectIsNotEditableNorAssignableToPlan() {
        Subject subject = completeDraft();
        subject.submitForApproval();

        assertThatThrownBy(subject::requireEditable).isInstanceOf(SubjectInvalidTransitionException.class);
        assertThatThrownBy(subject::requireAssignableToPlan).isInstanceOf(SubjectNotAssignableToPlanException.class);
    }

    @Test
    void codeCanBeCorrectedWhileDraftOrRejectedButNotOnceApproved() {
        Subject subject = completeDraft();
        subject.changeCode("ANA2");
        assertThat(subject.getCode()).isEqualTo("ANA2");

        subject.submitForApproval();
        subject.reject("Código incorrecto");
        subject.changeCode("ANA3");
        assertThat(subject.getCode()).isEqualTo("ANA3");

        subject.submitForApproval();
        subject.approve();
        assertThatThrownBy(() -> subject.changeCode("ANA4")).isInstanceOf(SubjectInvalidTransitionException.class);
        assertThatCode(() -> subject.changeCode("ANA3")).doesNotThrowAnyException();
    }

    @Test
    void activeSubjectStaysEditableAndAssignable() {
        Subject subject = completeDraft();
        subject.submitForApproval();
        subject.approve();

        assertThatCode(subject::requireEditable).doesNotThrowAnyException();
        assertThatCode(subject::requireAssignableToPlan).doesNotThrowAnyException();
        assertThat(subject.getStatus()).isEqualTo(SubjectStatus.ACTIVE);
    }

    @Test
    void draftSubjectIsNotAssignableToPlan() {
        Subject subject = completeDraft();

        assertThatThrownBy(subject::requireAssignableToPlan).isInstanceOf(SubjectNotAssignableToPlanException.class);
    }

    @Test
    void inactiveSubjectRemainsAssignableToPlan() {
        Subject subject = completeDraft();
        subject.submitForApproval();
        subject.approve();
        subject.deactivate();

        assertThatCode(subject::requireAssignableToPlan).doesNotThrowAnyException();
    }

    @Test
    void draftSubjectCannotOpenSections() {
        Subject subject = completeDraft();

        assertThatThrownBy(subject::requireOpenableForSections).isInstanceOf(SubjectNotOpenableForSectionsException.class);
    }

    @ParameterizedTest
    @EnumSource(value = SubjectStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void subjectsPastDraftCanOpenSections(SubjectStatus status) {
        Subject subject = completeDraft();
        subject.setStatus(status);

        assertThatCode(subject::requireOpenableForSections).doesNotThrowAnyException();
    }

    @Test
    void sectionsOpenOnceTheDirectorApprovesTheSubject() {
        Subject subject = completeDraft();
        subject.submitForApproval();
        subject.approve();

        assertThatCode(subject::requireOpenableForSections).doesNotThrowAnyException();
    }
}
