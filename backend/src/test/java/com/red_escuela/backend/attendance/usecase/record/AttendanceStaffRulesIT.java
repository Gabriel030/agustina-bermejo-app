package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.dto.CreateAttendanceChangeRequestRequest;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;
import com.red_escuela.backend.attendance.exception.AttendanceNotFoundException;
import com.red_escuela.backend.attendance.exception.CannotJustifyException;
import com.red_escuela.backend.attendance.exception.ClassSessionAlreadyCanceledException;
import com.red_escuela.backend.attendance.exception.ClassSessionCanceledException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.attendance.exception.InvalidAttendanceChangeRequestStateException;
import com.red_escuela.backend.attendance.repository.ClassSessionRepository;
import com.red_escuela.backend.attendance.usecase.changerequest.RejectAttendanceChangeRequestCommand;
import com.red_escuela.backend.attendance.usecase.changerequest.RejectAttendanceChangeRequestUseCase;
import com.red_escuela.backend.attendance.usecase.changerequest.RequestAttendanceChangeCommand;
import com.red_escuela.backend.attendance.usecase.changerequest.RequestAttendanceChangeUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Term;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.red_escuela.backend.shared.time.MutableClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendanceStaffRulesIT extends AbstractIntegrationTest {

    private static final LocalDate CLASS_DAY = LocalDate.of(2025, 9, 15);
    private static final long MISSING_ID = 987_654_321L;

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private LoadProgramSectionAttendanceUseCase loadAttendance;
    @Autowired
    private RejectAttendanceJustificationUseCase rejectJustification;
    @Autowired
    private SubmitAttendanceProofUseCase submitProof;
    @Autowired
    private GetProgramSectionAttendanceHistoryUseCase getHistory;
    @Autowired
    private RequestAttendanceChangeUseCase requestChange;
    @Autowired
    private RejectAttendanceChangeRequestUseCase rejectChange;
    @Autowired
    private CancelProgramSectionClassSessionUseCase cancelClass;
    @Autowired
    private ClassSessionRepository classSessionRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;

    private CycleTestFixtures fixtures;
    private SubjectSection section;
    private Instructor owner;
    private Instructor stranger;
    private Attendance absence;
    private SubjectSectionEnrollment enrollment;
    private SubjectSectionEnrollment enrollmentInAnotherSection;

    @BeforeEach
    void seedAbsenceInAnOwnedSection() {
        MutableClock.INSTANCE.setDate(CLASS_DAY.plusDays(1));
        fixtures = new CycleTestFixtures(entityManager);
        Term term = fixtures.term(2040 + (int) (System.nanoTime() % 50));
        var cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.APPROVED);
        section = fixtures.section(fixtures.subject(), term, com.red_escuela.backend.shared.domain.StatusType.ACTIVE, cycle);
        owner = fixtures.instructor();
        stranger = fixtures.instructor();
        SubjectSectionInstructor assignment = new SubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(owner);
        assignment.setRole(InstructorRole.LEAD);
        entityManager.persist(assignment);
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(fixtures.person());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(fixtures.program());
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(programEnrollment);
        enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        SubjectSection anotherSection = fixtures.section(fixtures.subject(), term, com.red_escuela.backend.shared.domain.StatusType.ACTIVE, cycle);
        enrollmentInAnotherSection = new SubjectSectionEnrollment();
        enrollmentInAnotherSection.setStudent(student);
        enrollmentInAnotherSection.setProgramEnrollment(programEnrollment);
        enrollmentInAnotherSection.setSubjectSection(anotherSection);
        enrollmentInAnotherSection.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollmentInAnotherSection.setEnrolledAt(LocalDateTime.now());
        enrollmentInAnotherSection.setAttemptNumber(1);
        entityManager.persist(enrollmentInAnotherSection);
        entityManager.flush();
        List<Attendance> loaded = loadAttendance.load(new LoadProgramSectionAttendanceCommand(section.getId(), owner.getId(),
                new CargarAsistenciaRequest(CLASS_DAY, List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT)), null, null)));
        absence = loaded.get(0);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void actAsStaffMember() {
        Person staff = fixtures.person();
        entityManager.flush();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(staff.getId().toString(), null));
    }

    private CreateAttendanceChangeRequestRequest changeToPresent() {
        return new CreateAttendanceChangeRequestRequest(AttendanceStatus.PRESENT, "student arrived late and was marked absent");
    }

    @Test
    void rejectingAJustificationOfAnAbsenceWithoutProofIsRefused() {
        assertThatThrownBy(() -> rejectJustification.reject(new RejectAttendanceJustificationCommand(absence.getId(), "illegible proof")))
                .isInstanceOf(CannotJustifyException.class);
        assertThat(absence.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(absence.getJustificationRejectionReason()).isNull();
    }

    @Test
    void rejectingAJustificationOfAnUnknownAttendanceReturnsNotFound() {
        assertThatThrownBy(() -> rejectJustification.reject(new RejectAttendanceJustificationCommand(MISSING_ID, "illegible proof")))
                .isInstanceOf(AttendanceNotFoundException.class);
    }

    @Test
    void rejectingAJustificationTrimsTheReasonAndSendsTheAbsenceBack() {
        submitProof.submit(new SubmitAttendanceProofCommand(absence.getId(), "medical visit", "students/1/proof.pdf"));
        actAsStaffMember();

        Attendance rejected = rejectJustification.reject(new RejectAttendanceJustificationCommand(absence.getId(), "  illegible proof  "));

        assertThat(rejected.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(rejected.getJustificationRejectionReason()).isEqualTo("illegible proof");
    }

    @Test
    void rejectingTheSameJustificationTwiceFailsTheSecondTime() {
        submitProof.submit(new SubmitAttendanceProofCommand(absence.getId(), "medical visit", "students/1/proof.pdf"));
        actAsStaffMember();
        rejectJustification.reject(new RejectAttendanceJustificationCommand(absence.getId(), "illegible proof"));

        assertThatThrownBy(() -> rejectJustification.reject(new RejectAttendanceJustificationCommand(absence.getId(), "illegible proof")))
                .isInstanceOf(CannotJustifyException.class);
    }

    private AttendanceHistoryResponse.HistoryCell historyCellOfTheAbsenceSeenBy(Long instructorId) {
        return getHistory.get(new GetProgramSectionAttendanceHistoryCommand(section.getId(), instructorId))
                .rows().get(0).cellsByDate().get(CLASS_DAY);
    }

    @Test
    void theOwnInstructorSeesTheRejectionReasonOfARejectedJustificationInTheHistory() {
        submitProof.submit(new SubmitAttendanceProofCommand(absence.getId(), "medical visit", "students/1/proof.pdf"));
        actAsStaffMember();
        rejectJustification.reject(new RejectAttendanceJustificationCommand(absence.getId(), "illegible proof"));

        AttendanceHistoryResponse.HistoryCell cell = historyCellOfTheAbsenceSeenBy(owner.getId());

        assertThat(cell.status()).isEqualTo("ABSENT");
        assertThat(cell.rejection().reason()).isEqualTo("illegible proof");
        assertThat(cell.rejection().rejectedAt()).isNotNull();
    }

    @Test
    void theHistoryHasNoRejectionWhenTheJustificationWasNeverRejected() {
        assertThat(historyCellOfTheAbsenceSeenBy(owner.getId()).rejection()).isNull();
    }

    @Test
    void theHistoryHasNoRejectionWhileTheRejectedAbsenceIsStillPendingANewProof() {
        submitProof.submit(new SubmitAttendanceProofCommand(absence.getId(), "medical visit", "students/1/proof.pdf"));

        assertThat(historyCellOfTheAbsenceSeenBy(owner.getId()).rejection()).isNull();
    }

    @Test
    void anInstructorOfAnotherSectionCannotSeeTheHistoryWithItsRejectionReasons() {
        submitProof.submit(new SubmitAttendanceProofCommand(absence.getId(), "medical visit", "students/1/proof.pdf"));
        actAsStaffMember();
        rejectJustification.reject(new RejectAttendanceJustificationCommand(absence.getId(), "illegible proof"));

        assertThatThrownBy(() -> historyCellOfTheAbsenceSeenBy(stranger.getId()))
                .isInstanceOf(NotOwnSectionException.class);
    }

    @Test
    void requestingAChangeOfAnUnknownAttendanceReturnsNotFound() {
        assertThatThrownBy(() -> requestChange.request(new RequestAttendanceChangeCommand(MISSING_ID, owner.getId(), changeToPresent())))
                .isInstanceOf(AttendanceNotFoundException.class);
    }

    @Test
    void anInstructorOfAnotherSectionCannotRequestAChange() {
        assertThatThrownBy(() -> requestChange.request(new RequestAttendanceChangeCommand(absence.getId(), stranger.getId(), changeToPresent())))
                .isInstanceOf(NotOwnSectionException.class);
    }

    @Test
    void theOwnInstructorRequestsAChangeThatStartsPendingAndKeepsThePreviousStatus() {
        AttendanceChangeRequest request = requestChange.request(new RequestAttendanceChangeCommand(absence.getId(), owner.getId(), changeToPresent()));

        assertThat(request.getStatus()).isEqualTo(AttendanceChangeRequestStatus.PENDING);
        assertThat(request.getPreviousStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(request.getRequestedStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(absence.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
    }

    @Test
    void rejectingAPendingChangeLeavesTheAttendanceUntouchedAndRecordsWhoRejected() {
        AttendanceChangeRequest request = requestChange.request(new RequestAttendanceChangeCommand(absence.getId(), owner.getId(), changeToPresent()));
        actAsStaffMember();

        AttendanceChangeRequest rejected = rejectChange.reject(new RejectAttendanceChangeRequestCommand(request.getId(), "Motivo suficiente"));

        assertThat(rejected.getStatus()).isEqualTo(AttendanceChangeRequestStatus.REJECTED);
        assertThat(rejected.getApprovedBy()).isNotNull();
        assertThat(rejected.getApprovedAt()).isNotNull();
        assertThat(rejected.getRejectionReason()).isEqualTo("Motivo suficiente");
        assertThat(absence.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
    }

    @Test
    void aChangeRequestAlreadyRejectedCannotBeRejectedAgain() {
        AttendanceChangeRequest request = requestChange.request(new RequestAttendanceChangeCommand(absence.getId(), owner.getId(), changeToPresent()));
        actAsStaffMember();
        rejectChange.reject(new RejectAttendanceChangeRequestCommand(request.getId(), "Motivo suficiente"));

        assertThatThrownBy(() -> rejectChange.reject(new RejectAttendanceChangeRequestCommand(request.getId(), "Motivo suficiente")))
                .isInstanceOf(InvalidAttendanceChangeRequestStateException.class);
    }

    private CargarAsistenciaRequest requestFor(LocalDate day, AttendanceEntry... entries) {
        return new CargarAsistenciaRequest(day, List.of(entries), null, null);
    }

    @Test
    void anEnrollmentOfAnotherSectionIsRejectedAndNothingOfTheBatchIsPersisted() {
        LocalDate otherDay = CLASS_DAY.plusDays(21);
        var request = requestFor(otherDay,
                new AttendanceEntry(enrollment.getId(), AttendanceStatus.PRESENT),
                new AttendanceEntry(enrollmentInAnotherSection.getId(), AttendanceStatus.ABSENT));

        assertThatThrownBy(() -> loadAttendance.load(new LoadProgramSectionAttendanceCommand(section.getId(), owner.getId(), request)))
                .isInstanceOf(SectionEnrollmentNotFoundException.class);

        assertThat(attendanceRepository.findByProgramSubjectSectionEnrollmentIdAndClassDate(enrollment.getId(), otherDay)).isEmpty();
        assertThat(attendanceRepository.findByProgramSubjectSectionEnrollmentIdAndClassDate(enrollmentInAnotherSection.getId(), otherDay)).isEmpty();
        assertThat(classSessionRepository.findBySubjectSectionIdAndClassDate(section.getId(), otherDay)).isEmpty();
    }

    @Test
    void aWithdrawnEnrollmentIsRejectedAndNothingIsPersisted() {
        LocalDate otherDay = CLASS_DAY.plusDays(28);
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        entityManager.flush();
        var request = requestFor(otherDay, new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT));

        assertThatThrownBy(() -> loadAttendance.load(new LoadProgramSectionAttendanceCommand(section.getId(), owner.getId(), request)))
                .isInstanceOf(SectionEnrollmentNotFoundException.class);

        assertThat(attendanceRepository.findByProgramSubjectSectionEnrollmentIdAndClassDate(enrollment.getId(), otherDay)).isEmpty();
    }

    @Test
    void attendanceOnACanceledClassIsRefusedAndNothingIsPersisted() {
        LocalDate otherDay = CLASS_DAY.plusDays(35);
        cancelClass.cancel(new CancelProgramSectionClassSessionCommand(section.getId(), owner.getId(), otherDay));
        var request = requestFor(otherDay, new AttendanceEntry(enrollment.getId(), AttendanceStatus.ABSENT));

        assertThatThrownBy(() -> loadAttendance.load(new LoadProgramSectionAttendanceCommand(section.getId(), owner.getId(), request)))
                .isInstanceOf(ClassSessionCanceledException.class);

        assertThat(attendanceRepository.findByProgramSubjectSectionEnrollmentIdAndClassDate(enrollment.getId(), otherDay)).isEmpty();
    }

    @Test
    void anActiveEnrollmentOfTheSectionIsStillRecorded() {
        LocalDate otherDay = CLASS_DAY.plusDays(42);

        var loaded = loadAttendance.load(new LoadProgramSectionAttendanceCommand(section.getId(), owner.getId(),
                requestFor(otherDay, new AttendanceEntry(enrollment.getId(), AttendanceStatus.PRESENT))));

        assertThat(loaded).hasSize(1);
    }

    @Test
    void cancelingAClassMarksItCanceledAndCancelingItAgainIsAConflict() {
        LocalDate otherDay = CLASS_DAY.plusDays(7);

        cancelClass.cancel(new CancelProgramSectionClassSessionCommand(section.getId(), owner.getId(), otherDay));

        assertThat(classSessionRepository.findBySubjectSectionIdAndClassDate(section.getId(), otherDay))
                .get().extracting(session -> session.getStatus()).isEqualTo(ClassSessionStatus.CANCELED);
        assertThatThrownBy(() -> cancelClass.cancel(new CancelProgramSectionClassSessionCommand(section.getId(), owner.getId(), otherDay)))
                .isInstanceOf(ClassSessionAlreadyCanceledException.class);
    }

    @Test
    void anInstructorOfAnotherSectionCannotCancelTheClassAndNothingIsCreated() {
        LocalDate otherDay = CLASS_DAY.plusDays(14);

        assertThatThrownBy(() -> cancelClass.cancel(new CancelProgramSectionClassSessionCommand(section.getId(), stranger.getId(), otherDay)))
                .isInstanceOf(NotOwnSectionException.class);
        assertThat(classSessionRepository.findBySubjectSectionIdAndClassDate(section.getId(), otherDay)).isEmpty();
    }

    @Test
    void cancelingAClassOfAnUnknownSectionReturnsNotFound() {
        assertThatThrownBy(() -> cancelClass.cancel(new CancelProgramSectionClassSessionCommand(MISSING_ID, owner.getId(), CLASS_DAY)))
                .isInstanceOf(SectionNotFoundException.class);
    }
}
