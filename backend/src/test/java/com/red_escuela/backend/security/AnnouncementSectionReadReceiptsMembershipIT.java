package com.red_escuela.backend.security;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
import com.red_escuela.backend.announcement.entity.AnnouncementRecipient;
import com.red_escuela.backend.announcement.entity.AnnouncementStatus;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.specialist.PermissionDiscrepancyAccumulator;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.permission.PermissionSnapshotCache;
import com.red_escuela.backend.testsupport.SlowTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SlowTest(reason = "recorre los endpoints de read-receipts por seccion segun modo del piloto contra el contexto completo")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AnnouncementSectionReadReceiptsMembershipIT extends AuthorizationMatrixSupport {

    private static final List<String> ROLES = List.of("ALUMNO", "DOCENTE", "PRECEPTOR", "ADMINISTRATIVO", "DIRECTOR", "PLATFORM_ADMIN");

    private enum Kind { SUBJECT, COURSE }

    private record Teacher(Long personId, String token) {
    }

    private record Seeded(Kind kind, Long sectionId) {
        String path() {
            return (kind == Kind.SUBJECT ? "/subject-sections/" : "/course-sections/") + sectionId + "/announcements/read-receipts";
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PermissionSnapshotCache permissionCache;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private PermissionDiscrepancyAccumulator discrepancyAccumulator;
    @Autowired
    private RoleRepository sectionRoleRepository;
    @Autowired
    private JwtService sectionJwtService;

    @AfterEach
    void restoreDefaultGrid() {
        jdbcTemplate.update("delete from permission_module_mode");
        jdbcTemplate.update("delete from role_permission where role in ('DOCENTE', 'PRECEPTOR') and permission_id = (select id from permission where code = 'announcement.view_read_receipts')");
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'PRECEPTOR', id from permission where code = 'announcement.view_read_receipts'");
        discrepancyAccumulator.flush();
        jdbcTemplate.update("delete from permission_discrepancy_counter");
        permissionCache.invalidate();
    }

    private void setMode(String mode) {
        for (String role : ROLES) {
            jdbcTemplate.update("insert into permission_module_mode (role, module, mode) values (?, 'announcement', ?)", role, mode);
        }
        permissionCache.invalidate();
    }

    private void grantReceiptsToDocente() {
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'DOCENTE', id from permission where code = 'announcement.view_read_receipts'");
        permissionCache.invalidate();
    }

    private Teacher teacherWithRoles(String... roleNames) {
        return transactionTemplate.execute(status -> {
            Person person = new CycleTestFixtures(entityManager).person();
            for (String roleName : roleNames) {
                person.getRoles().add(sectionRoleRepository.findByName(roleName).orElseThrow());
            }
            Instructor instructor = new Instructor();
            instructor.setPerson(person);
            entityManager.persist(instructor);
            entityManager.flush();
            return new Teacher(person.getId(), sectionJwtService.generateAccessToken(person));
        });
    }

    private Seeded seedSectionTaughtBy(Kind kind, Teacher teacher) {
        return transactionTemplate.execute(status -> {
            CycleTestFixtures cycleFixtures = new CycleTestFixtures(entityManager);
            Instructor instructor = entityManager.createQuery("select i from Instructor i where i.person.id = :personId", Instructor.class)
                    .setParameter("personId", teacher.personId()).getSingleResult();
            var term = cycleFixtures.term(2100 + (int) (System.nanoTime() % 800));
            Announcement.AnnouncementBuilder announcement = Announcement.builder()
                    .createdBy(cycleFixtures.person())
                    .title("Aviso")
                    .body("Cuerpo")
                    .category(AnnouncementCategory.REMINDER)
                    .audienceSpec(AudienceSpec.wholeSection())
                    .audienceLabel("Toda la comision")
                    .status(AnnouncementStatus.SENT)
                    .sentAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now());
            Long sectionId;
            if (kind == Kind.SUBJECT) {
                var cycle = cycleFixtures.programCycle(cycleFixtures.program(), term, AcademicOfferingCycleStatus.APPROVED);
                SubjectSection section = cycleFixtures.section(cycleFixtures.subject(), term, StatusType.ACTIVE, cycle);
                cycleFixtures.assignLead(section, instructor);
                announcement.subjectSection(section);
                sectionId = section.getId();
            } else {
                Course course = new Course();
                course.setName("Curso");
                course.setTerm(term);
                entityManager.persist(course);
                CourseSubject courseSubject = new CourseSubject();
                courseSubject.setCourse(course);
                courseSubject.setName("Modulo");
                entityManager.persist(courseSubject);
                CourseSubjectSection section = new CourseSubjectSection();
                section.setName("Comision unica");
                section.setCourseSubject(courseSubject);
                section.setTerm(term);
                section.setStatus(StatusType.ACTIVE);
                entityManager.persist(section);
                CourseSubjectSectionInstructor assignment = new CourseSubjectSectionInstructor();
                assignment.setSection(section);
                assignment.setInstructor(instructor);
                assignment.setRole(InstructorRole.LEAD);
                entityManager.persist(assignment);
                announcement.courseSubjectSection(section);
                sectionId = section.getId();
            }
            Announcement saved = announcement.build();
            entityManager.persist(saved);
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(cycleFixtures.person());
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            entityManager.persist(AnnouncementRecipient.builder()
                    .announcement(saved).student(student).readAt(LocalDateTime.now()).build());
            entityManager.flush();
            return new Seeded(kind, sectionId);
        });
    }

    private int statusWith(Seeded seeded, String token) throws Exception {
        var response = mockMvc.perform(get(seeded.path()).header("Authorization", "Bearer " + token)).andReturn().getResponse();
        lastResponseBody = response.getContentAsString();
        return response.getStatus();
    }

    private void assertListsWithCounts(Seeded seeded, String token, String label) throws Exception {
        assertThat(statusWith(seeded, token)).as(label + " " + lastResponseBody).isEqualTo(200);
        assertThat(lastResponseBody).as(label).contains("\"recipientCount\":1").contains("\"readCount\":1");
    }

    private void assertMembershipRules(String label, Kind kind, boolean docenteHasThePermission) throws Exception {
        Teacher owner = teacherWithRoles("DOCENTE");
        Teacher stranger = teacherWithRoles("DOCENTE");
        Teacher doubleRoleOutsider = teacherWithRoles("DOCENTE", "PRECEPTOR");
        Seeded seeded = seedSectionTaughtBy(kind, owner);

        assertListsWithCounts(seeded, fixtures.tokenFor(MatrixRole.PRECEPTOR), label + " preceptor");
        assertListsWithCounts(seeded, fixtures.tokenFor(MatrixRole.DIRECTOR), label + " director");
        assertListsWithCounts(seeded, doubleRoleOutsider.token(), label + " docente and preceptor outside the section");
        if (docenteHasThePermission) {
            assertListsWithCounts(seeded, owner.token(), label + " docente of the section");
            assertThat(statusWith(seeded, stranger.token())).as(label + " docente of another section " + lastResponseBody).isEqualTo(404);
        } else {
            assertThat(statusWith(seeded, owner.token())).as(label + " docente without the permission").isEqualTo(403);
            assertThat(statusWith(seeded, stranger.token())).as(label + " stranger without the permission").isEqualTo(403);
        }
    }

    @Test
    void subjectSectionFollowsTheOldRoleRulesInLegacyMode() throws Exception {
        assertMembershipRules("LEGACY subject", Kind.SUBJECT, false);
    }

    @Test
    void courseSectionFollowsTheOldRoleRulesInLegacyMode() throws Exception {
        assertMembershipRules("LEGACY course", Kind.COURSE, false);
    }

    @Test
    void subjectSectionFollowsTheOldRoleRulesInShadowModeEvenWithThePermissionGrantedToDocente() throws Exception {
        setMode("SHADOW");
        grantReceiptsToDocente();
        assertMembershipRules("SHADOW subject", Kind.SUBJECT, false);
    }

    @Test
    void courseSectionFollowsTheOldRoleRulesInShadowModeEvenWithThePermissionGrantedToDocente() throws Exception {
        setMode("SHADOW");
        grantReceiptsToDocente();
        assertMembershipRules("SHADOW course", Kind.COURSE, false);
    }

    @Test
    void subjectSectionRequiresMembershipForDocenteInEnforceMode() throws Exception {
        setMode("ENFORCE");
        grantReceiptsToDocente();
        assertMembershipRules("ENFORCE subject", Kind.SUBJECT, true);
    }

    @Test
    void courseSectionRequiresMembershipForDocenteInEnforceMode() throws Exception {
        setMode("ENFORCE");
        grantReceiptsToDocente();
        assertMembershipRules("ENFORCE course", Kind.COURSE, true);
    }

    @Test
    void enforceModeWithoutTheGrantKeepsDocenteOutOfBothListings() throws Exception {
        setMode("ENFORCE");
        assertMembershipRules("ENFORCE default subject", Kind.SUBJECT, false);
        assertMembershipRules("ENFORCE default course", Kind.COURSE, false);
    }
}
