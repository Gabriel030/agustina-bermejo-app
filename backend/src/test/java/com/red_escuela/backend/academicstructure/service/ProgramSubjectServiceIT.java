package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.dto.AddCorequisiteRequest;
import com.red_escuela.backend.academicstructure.dto.AddPrerequisiteRequest;
import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.entity.ElectiveGroup;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramElectiveGroup;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCorequisite;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectRecurrence;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.exception.CircularPrerequisiteException;
import com.red_escuela.backend.academicstructure.exception.CorequisiteAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.CorequisiteNotFoundException;
import com.red_escuela.backend.academicstructure.exception.CurricularYearOutOfRangeException;
import com.red_escuela.backend.academicstructure.exception.ElectiveGroupNotAssignedException;
import com.red_escuela.backend.academicstructure.exception.ElectiveGroupNotFoundException;
import com.red_escuela.backend.academicstructure.exception.InvalidCorequisiteException;
import com.red_escuela.backend.academicstructure.exception.InvalidPrerequisiteException;
import com.red_escuela.backend.academicstructure.exception.InvalidProgramSubjectCategoryException;
import com.red_escuela.backend.academicstructure.exception.PrerequisiteAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.PrerequisiteNotFoundException;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.exception.ProgramSubjectAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.ProgramSubjectNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SubjectNotAssignableToPlanException;
import com.red_escuela.backend.academicstructure.exception.SubjectNotFoundException;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProgramSubjectServiceIT extends AbstractIntegrationTest {

    private static final long MISSING_ID = 987_654_321L;

    @Autowired
    private ProgramSubjectService service;
    @Autowired
    private EntityManager entityManager;

    private CycleTestFixtures fixtures;
    private Program program;
    private ElectiveGroup assignedGroup;

    @BeforeEach
    void seedAProgramWithAnAssignedElectiveGroup() {
        fixtures = new CycleTestFixtures(entityManager);
        program = fixtures.program();
        assignedGroup = electiveGroup();
        ProgramElectiveGroup link = new ProgramElectiveGroup();
        link.setProgram(program);
        link.setElectiveGroup(assignedGroup);
        link.setMinRequired(1);
        link.setMaxAllowed(2);
        entityManager.persist(link);
        entityManager.flush();
    }

    private ElectiveGroup electiveGroup() {
        ElectiveGroup group = new ElectiveGroup();
        group.setName("Grupo " + UUID.randomUUID());
        entityManager.persist(group);
        return group;
    }

    private static CreateProgramSubjectRequest mandatory(Subject subject, Integer year, CurricularTerm term) {
        return new CreateProgramSubjectRequest(subject.getId(), ProgramSubjectCategory.MANDATORY, year, term, 6,
                SubjectRecurrence.ANNUAL, 1, null);
    }

    private ProgramSubject addMandatory(Program target, Integer year, CurricularTerm term) {
        return service.addSubject(target.getId(), mandatory(fixtures.subject(), year, term));
    }

    private static AddPrerequisiteRequest prerequisite(ProgramSubject prerequisite) {
        return new AddPrerequisiteRequest(prerequisite.getId(), RequirementType.APPROVED, null, null);
    }

    @Test
    void addingAMandatorySubjectStoresItsPlacementAndStartsActive() {
        ProgramSubject added = addMandatory(program, 2, CurricularTerm.FIRST);

        assertThat(added.getProgram().getId()).isEqualTo(program.getId());
        assertThat(added.getCategory()).isEqualTo(ProgramSubjectCategory.MANDATORY);
        assertThat(added.getCurricularYear()).isEqualTo(2);
        assertThat(added.getCurricularTerm()).isEqualTo(CurricularTerm.FIRST);
        assertThat(added.getCredits()).isEqualTo(6);
        assertThat(added.getStatus()).isEqualTo(StatusType.ACTIVE);
        assertThat(added.getElectiveGroup()).isNull();
    }

    @Test
    void addingAnElectiveSubjectLinksItToTheAssignedGroup() {
        Subject subject = fixtures.subject();

        ProgramSubject added = service.addSubject(program.getId(), new CreateProgramSubjectRequest(subject.getId(),
                ProgramSubjectCategory.ELECTIVE, null, null, 3, null, null, assignedGroup.getId()));

        assertThat(added.getElectiveGroup().getId()).isEqualTo(assignedGroup.getId());
    }

    @Test
    void addingAnAdditionalMandatorySubjectWithoutCurricularPlacementIsAccepted() {
        Subject subject = fixtures.subject();

        ProgramSubject added = service.addSubject(program.getId(), new CreateProgramSubjectRequest(subject.getId(),
                ProgramSubjectCategory.ADDITIONAL_MANDATORY, null, null, 0, null, null, null));

        assertThat(added.getCurricularYear()).isNull();
    }

    @Test
    void addingToAMissingProgramOrWithAMissingSubjectFailsWithNotFound() {
        Subject subject = fixtures.subject();

        assertThatThrownBy(() -> service.addSubject(MISSING_ID, mandatory(subject, 1, CurricularTerm.FIRST)))
                .isInstanceOf(ProgramNotFoundException.class);
        assertThatThrownBy(() -> service.addSubject(program.getId(), new CreateProgramSubjectRequest(MISSING_ID,
                ProgramSubjectCategory.MANDATORY, 1, CurricularTerm.FIRST, 1, null, null, null)))
                .isInstanceOf(SubjectNotFoundException.class);
    }

    @Test
    void aSubjectStillInDraftCannotBeAddedToAPlan() {
        Subject draft = fixtures.subject();
        draft.setStatus(SubjectStatus.DRAFT);
        entityManager.flush();

        assertThatThrownBy(() -> service.addSubject(program.getId(), mandatory(draft, 1, CurricularTerm.FIRST)))
                .isInstanceOf(SubjectNotAssignableToPlanException.class);
    }

    @Test
    void theSameSubjectCannotBeAddedTwiceToTheSameProgram() {
        Subject subject = fixtures.subject();
        service.addSubject(program.getId(), mandatory(subject, 1, CurricularTerm.FIRST));

        assertThatThrownBy(() -> service.addSubject(program.getId(), mandatory(subject, 2, CurricularTerm.SECOND)))
                .isInstanceOf(ProgramSubjectAlreadyExistsException.class);
    }

    @Test
    void anElectiveGroupThatDoesNotExistOrIsNotAssignedToTheProgramIsRejected() {
        ElectiveGroup foreign = electiveGroup();
        entityManager.flush();

        assertThatThrownBy(() -> service.addSubject(program.getId(), new CreateProgramSubjectRequest(fixtures.subject().getId(),
                ProgramSubjectCategory.ELECTIVE, null, null, 3, null, null, MISSING_ID)))
                .isInstanceOf(ElectiveGroupNotFoundException.class);
        assertThatThrownBy(() -> service.addSubject(program.getId(), new CreateProgramSubjectRequest(fixtures.subject().getId(),
                ProgramSubjectCategory.ELECTIVE, null, null, 3, null, null, foreign.getId())))
                .isInstanceOf(ElectiveGroupNotAssignedException.class);
    }

    @Test
    void aMandatorySubjectRequiresYearAndTerm() {
        assertThatThrownBy(() -> service.addSubject(program.getId(), mandatory(fixtures.subject(), null, CurricularTerm.FIRST)))
                .isInstanceOf(InvalidProgramSubjectCategoryException.class);
        assertThatThrownBy(() -> service.addSubject(program.getId(), mandatory(fixtures.subject(), 1, null)))
                .isInstanceOf(InvalidProgramSubjectCategoryException.class);
    }

    @Test
    void anElectiveSubjectRequiresAnElectiveGroup() {
        assertThatThrownBy(() -> service.addSubject(program.getId(), new CreateProgramSubjectRequest(fixtures.subject().getId(),
                ProgramSubjectCategory.ELECTIVE, null, null, 3, null, null, null)))
                .isInstanceOf(InvalidProgramSubjectCategoryException.class);
    }

    @Test
    void anAdditionalMandatorySubjectCannotHaveYearOrTerm() {
        assertThatThrownBy(() -> service.addSubject(program.getId(), new CreateProgramSubjectRequest(fixtures.subject().getId(),
                ProgramSubjectCategory.ADDITIONAL_MANDATORY, 1, null, 0, null, null, null)))
                .isInstanceOf(InvalidProgramSubjectCategoryException.class);
        assertThatThrownBy(() -> service.addSubject(program.getId(), new CreateProgramSubjectRequest(fixtures.subject().getId(),
                ProgramSubjectCategory.ADDITIONAL_MANDATORY, null, CurricularTerm.FIRST, 0, null, null, null)))
                .isInstanceOf(InvalidProgramSubjectCategoryException.class);
    }

    @Test
    void aCurricularYearBeyondTheProgramDurationIsRejected() {
        assertThatThrownBy(() -> addMandatory(program, 4, CurricularTerm.FIRST)).isInstanceOf(CurricularYearOutOfRangeException.class);
        assertThatThrownBy(() -> addMandatory(program, 0, CurricularTerm.FIRST)).isInstanceOf(CurricularYearOutOfRangeException.class);
    }

    @Test
    void updatingOnlyOneFieldKeepsTheRest() {
        ProgramSubject added = addMandatory(program, 1, CurricularTerm.FIRST);

        ProgramSubject updated = service.update(added.getId(), new UpdateProgramSubjectRequest(null, null, 9, null, null, null));

        assertThat(updated.getCredits()).isEqualTo(9);
        assertThat(updated.getCurricularYear()).isEqualTo(1);
        assertThat(updated.getCurricularTerm()).isEqualTo(CurricularTerm.FIRST);
        assertThat(updated.getSubjectRecurrence()).isEqualTo(SubjectRecurrence.ANNUAL);
        assertThat(updated.getSortOrder()).isEqualTo(1);
    }

    @Test
    void updatingEveryFieldStoresTheNewValues() {
        ProgramSubject added = addMandatory(program, 1, CurricularTerm.FIRST);

        ProgramSubject updated = service.update(added.getId(), new UpdateProgramSubjectRequest(
                3, CurricularTerm.SECOND, 12, SubjectRecurrence.PROMOTIONAL, 8, assignedGroup.getId()));

        assertThat(updated.getCurricularYear()).isEqualTo(3);
        assertThat(updated.getCurricularTerm()).isEqualTo(CurricularTerm.SECOND);
        assertThat(updated.getCredits()).isEqualTo(12);
        assertThat(updated.getSubjectRecurrence()).isEqualTo(SubjectRecurrence.PROMOTIONAL);
        assertThat(updated.getSortOrder()).isEqualTo(8);
        assertThat(updated.getElectiveGroup().getId()).isEqualTo(assignedGroup.getId());
    }

    @Test
    void updatingToAYearBeyondTheProgramDurationIsRejected() {
        ProgramSubject added = addMandatory(program, 1, CurricularTerm.FIRST);

        assertThatThrownBy(() -> service.update(added.getId(), new UpdateProgramSubjectRequest(4, null, null, null, null, null)))
                .isInstanceOf(CurricularYearOutOfRangeException.class);
    }

    @Test
    void updatingWithAnElectiveGroupThatIsMissingOrNotAssignedIsRejected() {
        ProgramSubject added = addMandatory(program, 1, CurricularTerm.FIRST);
        ElectiveGroup foreign = electiveGroup();
        entityManager.flush();

        assertThatThrownBy(() -> service.update(added.getId(), new UpdateProgramSubjectRequest(null, null, null, null, null, MISSING_ID)))
                .isInstanceOf(ElectiveGroupNotFoundException.class);
        assertThatThrownBy(() -> service.update(added.getId(), new UpdateProgramSubjectRequest(null, null, null, null, null, foreign.getId())))
                .isInstanceOf(ElectiveGroupNotAssignedException.class);
    }

    @Test
    void gettingOrUpdatingAMissingProgramSubjectFailsWithNotFound() {
        assertThatThrownBy(() -> service.get(MISSING_ID)).isInstanceOf(ProgramSubjectNotFoundException.class);
        assertThatThrownBy(() -> service.update(MISSING_ID, new UpdateProgramSubjectRequest(null, null, 1, null, null, null)))
                .isInstanceOf(ProgramSubjectNotFoundException.class);
    }

    @Test
    void listByProgramFiltersByStatusAndIgnoresOtherPrograms() {
        ProgramSubject active = addMandatory(program, 1, CurricularTerm.FIRST);
        ProgramSubject inactive = addMandatory(program, 1, CurricularTerm.SECOND);
        addMandatory(fixtures.program(), 1, CurricularTerm.FIRST);
        service.deactivate(inactive.getId());
        entityManager.flush();

        assertThat(service.listByProgram(program.getId(), null)).extracting(ProgramSubject::getId)
                .containsExactlyInAnyOrder(active.getId(), inactive.getId());
        assertThat(service.listByProgram(program.getId(), StatusType.ACTIVE)).extracting(ProgramSubject::getId)
                .containsExactly(active.getId());
        assertThat(service.listByProgram(program.getId(), StatusType.INACTIVE)).extracting(ProgramSubject::getId)
                .containsExactly(inactive.getId());
    }

    @Test
    void deactivatingAndReactivatingTogglesTheStatus() {
        ProgramSubject added = addMandatory(program, 1, CurricularTerm.FIRST);

        service.deactivate(added.getId());
        assertThat(service.get(added.getId()).getStatus()).isEqualTo(StatusType.INACTIVE);

        service.reactivate(added.getId());
        assertThat(service.get(added.getId()).getStatus()).isEqualTo(StatusType.ACTIVE);
    }

    @Test
    void deactivatingOrReactivatingAMissingProgramSubjectFailsWithNotFound() {
        assertThatThrownBy(() -> service.deactivate(MISSING_ID)).isInstanceOf(ProgramSubjectNotFoundException.class);
        assertThatThrownBy(() -> service.reactivate(MISSING_ID)).isInstanceOf(ProgramSubjectNotFoundException.class);
    }

    @Test
    void aPrerequisiteIsStoredWithItsMinimumGradeAndNotes() {
        ProgramSubject early = addMandatory(program, 1, CurricularTerm.FIRST);
        ProgramSubject late = addMandatory(program, 2, CurricularTerm.FIRST);

        ProgramSubjectPrerequisite stored = service.addPrerequisite(late.getId(),
                new AddPrerequisiteRequest(early.getId(), RequirementType.PASSED_COURSEWORK, new BigDecimal("7"), "Con cursada"));

        assertThat(stored.getProgramSubject().getId()).isEqualTo(late.getId());
        assertThat(stored.getPrerequisiteProgramSubject().getId()).isEqualTo(early.getId());
        assertThat(stored.getRequirementType()).isEqualTo(RequirementType.PASSED_COURSEWORK);
        assertThat(stored.getMinGrade()).isEqualTo(Grade.of(new BigDecimal("7")));
        assertThat(stored.getNotes()).isEqualTo("Con cursada");
        assertThat(service.listPrerequisites(late.getId())).extracting(ProgramSubjectPrerequisite::getId).containsExactly(stored.getId());
    }

    @Test
    void aPrerequisiteWithoutMinimumGradeKeepsItNull() {
        ProgramSubject early = addMandatory(program, 1, CurricularTerm.FIRST);
        ProgramSubject late = addMandatory(program, 2, CurricularTerm.FIRST);

        ProgramSubjectPrerequisite stored = service.addPrerequisite(late.getId(), prerequisite(early));

        assertThat(stored.getMinGrade()).isNull();
    }

    @Test
    void aSubjectCannotBeItsOwnPrerequisite() {
        ProgramSubject only = addMandatory(program, 1, CurricularTerm.FIRST);

        assertThatThrownBy(() -> service.addPrerequisite(only.getId(), prerequisite(only)))
                .isInstanceOf(InvalidPrerequisiteException.class);
    }

    @Test
    void prerequisitesAcrossDifferentProgramsAreRejected() {
        ProgramSubject mine = addMandatory(program, 2, CurricularTerm.FIRST);
        ProgramSubject foreign = addMandatory(fixtures.program(), 1, CurricularTerm.FIRST);

        assertThatThrownBy(() -> service.addPrerequisite(mine.getId(), prerequisite(foreign)))
                .isInstanceOf(InvalidPrerequisiteException.class);
    }

    @Test
    void aPrerequisiteMustFinishBeforeTheTargetSubjectStarts() {
        ProgramSubject firstTerm = addMandatory(program, 2, CurricularTerm.FIRST);
        ProgramSubject sameTerm = addMandatory(program, 2, CurricularTerm.FIRST);
        ProgramSubject secondTerm = addMandatory(program, 2, CurricularTerm.SECOND);
        ProgramSubject annual = addMandatory(program, 2, CurricularTerm.ANNUAL);
        ProgramSubject laterYear = addMandatory(program, 3, CurricularTerm.FIRST);

        assertThatThrownBy(() -> service.addPrerequisite(firstTerm.getId(), prerequisite(sameTerm)))
                .isInstanceOf(InvalidPrerequisiteException.class);
        assertThatThrownBy(() -> service.addPrerequisite(firstTerm.getId(), prerequisite(secondTerm)))
                .isInstanceOf(InvalidPrerequisiteException.class);
        assertThatThrownBy(() -> service.addPrerequisite(secondTerm.getId(), prerequisite(annual)))
                .isInstanceOf(InvalidPrerequisiteException.class);
        assertThatThrownBy(() -> service.addPrerequisite(firstTerm.getId(), prerequisite(laterYear)))
                .isInstanceOf(InvalidPrerequisiteException.class);
        assertThat(service.addPrerequisite(secondTerm.getId(), prerequisite(firstTerm))).isNotNull();
        assertThat(service.addPrerequisite(laterYear.getId(), prerequisite(annual))).isNotNull();
    }

    @Test
    void anAnnualSubjectOfAnEarlierYearCanBeRequired() {
        ProgramSubject annual = addMandatory(program, 1, CurricularTerm.ANNUAL);
        ProgramSubject nextYearFirstTerm = addMandatory(program, 2, CurricularTerm.FIRST);

        assertThat(service.addPrerequisite(nextYearFirstTerm.getId(), prerequisite(annual))).isNotNull();
    }

    @Test
    void chronologyIsNotCheckedWhenAnyPlacementIsMissing() {
        ProgramSubject placed = addMandatory(program, 1, CurricularTerm.FIRST);
        ProgramSubject unplaced = service.addSubject(program.getId(), additional());
        ProgramSubject otherUnplaced = service.addSubject(program.getId(), additional());

        assertThat(service.addPrerequisite(placed.getId(), prerequisite(unplaced))).isNotNull();
        assertThat(service.addPrerequisite(otherUnplaced.getId(), prerequisite(placed))).isNotNull();
    }

    @Test
    void theSamePrerequisiteWithTheSameRequirementTypeCannotBeAddedTwice() {
        ProgramSubject early = addMandatory(program, 1, CurricularTerm.FIRST);
        ProgramSubject late = addMandatory(program, 2, CurricularTerm.FIRST);
        service.addPrerequisite(late.getId(), prerequisite(early));

        assertThatThrownBy(() -> service.addPrerequisite(late.getId(), prerequisite(early)))
                .isInstanceOf(PrerequisiteAlreadyExistsException.class);
        assertThat(service.addPrerequisite(late.getId(),
                new AddPrerequisiteRequest(early.getId(), RequirementType.PASSED_COURSEWORK, null, null))).isNotNull();
    }

    @Test
    void aPrerequisiteThatClosesACycleIsRejected() {
        ProgramSubject first = service.addSubject(program.getId(), additional());
        ProgramSubject second = service.addSubject(program.getId(), additional());
        ProgramSubject third = service.addSubject(program.getId(), additional());
        service.addPrerequisite(second.getId(), prerequisite(first));
        service.addPrerequisite(third.getId(), prerequisite(second));

        assertThatThrownBy(() -> service.addPrerequisite(first.getId(), prerequisite(third)))
                .isInstanceOf(CircularPrerequisiteException.class);
    }

    @Test
    void aDiamondOfPrerequisitesIsNotACycle() {
        ProgramSubject root = service.addSubject(program.getId(), additional());
        ProgramSubject left = service.addSubject(program.getId(), additional());
        ProgramSubject right = service.addSubject(program.getId(), additional());
        ProgramSubject top = service.addSubject(program.getId(), additional());
        service.addPrerequisite(left.getId(), prerequisite(root));
        service.addPrerequisite(right.getId(), prerequisite(root));
        service.addPrerequisite(top.getId(), prerequisite(left));

        assertThat(service.addPrerequisite(top.getId(), prerequisite(right))).isNotNull();
    }

    @Test
    void addingAPrerequisiteToMissingSubjectsFailsWithNotFound() {
        ProgramSubject existing = addMandatory(program, 1, CurricularTerm.FIRST);

        assertThatThrownBy(() -> service.addPrerequisite(MISSING_ID, prerequisite(existing)))
                .isInstanceOf(ProgramSubjectNotFoundException.class);
        assertThatThrownBy(() -> service.addPrerequisite(existing.getId(),
                new AddPrerequisiteRequest(MISSING_ID, RequirementType.APPROVED, null, null)))
                .isInstanceOf(ProgramSubjectNotFoundException.class);
    }

    @Test
    void removingAPrerequisiteDeletesItAndRemovingAMissingOneFailsWithNotFound() {
        ProgramSubject early = addMandatory(program, 1, CurricularTerm.FIRST);
        ProgramSubject late = addMandatory(program, 2, CurricularTerm.FIRST);
        ProgramSubjectPrerequisite stored = service.addPrerequisite(late.getId(), prerequisite(early));

        service.removePrerequisite(stored.getId());
        entityManager.flush();

        assertThat(service.listPrerequisites(late.getId())).isEmpty();
        assertThatThrownBy(() -> service.removePrerequisite(stored.getId())).isInstanceOf(PrerequisiteNotFoundException.class);
    }

    @Test
    void aCorequisiteLinksTwoSubjectsOfTheSameProgram() {
        ProgramSubject first = addMandatory(program, 1, CurricularTerm.FIRST);
        ProgramSubject second = addMandatory(program, 1, CurricularTerm.FIRST);

        ProgramSubjectCorequisite stored = service.addCorequisite(first.getId(), new AddCorequisiteRequest(second.getId()));

        assertThat(stored.getProgramSubject().getId()).isEqualTo(first.getId());
        assertThat(stored.getCorequisiteProgramSubject().getId()).isEqualTo(second.getId());
        assertThat(service.listCorequisites(first.getId())).extracting(ProgramSubjectCorequisite::getId).containsExactly(stored.getId());
    }

    @Test
    void aSubjectCannotBeItsOwnCorequisite() {
        ProgramSubject only = addMandatory(program, 1, CurricularTerm.FIRST);

        assertThatThrownBy(() -> service.addCorequisite(only.getId(), new AddCorequisiteRequest(only.getId())))
                .isInstanceOf(InvalidCorequisiteException.class);
    }

    @Test
    void corequisitesAcrossDifferentProgramsAreRejected() {
        ProgramSubject mine = addMandatory(program, 1, CurricularTerm.FIRST);
        ProgramSubject foreign = addMandatory(fixtures.program(), 1, CurricularTerm.FIRST);

        assertThatThrownBy(() -> service.addCorequisite(mine.getId(), new AddCorequisiteRequest(foreign.getId())))
                .isInstanceOf(InvalidCorequisiteException.class);
    }

    @Test
    void theSameCorequisiteCannotBeAddedTwice() {
        ProgramSubject first = addMandatory(program, 1, CurricularTerm.FIRST);
        ProgramSubject second = addMandatory(program, 1, CurricularTerm.FIRST);
        service.addCorequisite(first.getId(), new AddCorequisiteRequest(second.getId()));

        assertThatThrownBy(() -> service.addCorequisite(first.getId(), new AddCorequisiteRequest(second.getId())))
                .isInstanceOf(CorequisiteAlreadyExistsException.class);
    }

    @Test
    void removingACorequisiteDeletesItAndRemovingAMissingOneFailsWithNotFound() {
        ProgramSubject first = addMandatory(program, 1, CurricularTerm.FIRST);
        ProgramSubject second = addMandatory(program, 1, CurricularTerm.FIRST);
        ProgramSubjectCorequisite stored = service.addCorequisite(first.getId(), new AddCorequisiteRequest(second.getId()));

        service.removeCorequisite(stored.getId());
        entityManager.flush();

        assertThat(service.listCorequisites(first.getId())).isEmpty();
        assertThatThrownBy(() -> service.removeCorequisite(stored.getId())).isInstanceOf(CorequisiteNotFoundException.class);
    }

    private CreateProgramSubjectRequest additional() {
        return new CreateProgramSubjectRequest(fixtures.subject().getId(), ProgramSubjectCategory.ADDITIONAL_MANDATORY,
                null, null, 0, null, null, null);
    }
}
