package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ListStaffServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String MARK = "LSTCOV";

    @Autowired
    private ListStaffUseCase listStaffUseCase;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seedPeople() {
        persist("A1", "DOCENTE", StatusType.ACTIVE, "Ana");
        persist("A2", "PRECEPTOR", StatusType.ACTIVE, "Beto");
        persist("A3", "ADMINISTRATIVO", StatusType.ACTIVE, "Carla");
        persist("A4", "ALUMNO", StatusType.ACTIVE, "Dario");
        persist("A5", "DOCENTE", StatusType.INACTIVE, "Elena");
        entityManager.flush();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Person persist(String suffix, String roleName, StatusType status, String name) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName(name);
        person.setLastname(MARK + suffix);
        person.setDni(MARK + suffix);
        person.setUsername(MARK + suffix);
        person.setEmail(MARK.toLowerCase() + suffix.toLowerCase() + "@example.com");
        person.setAddress(address);
        person.setStatus(status);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        return person;
    }

    private void actAs(String roleName) {
        Person actor = persist("ACT" + roleName, roleName, StatusType.ACTIVE, "Actor");
        actor.setLastname("ACTOR" + roleName);
        entityManager.flush();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(actor.getId().toString(), null));
    }

    private List<String> lastnames(ListStaffCommand command) {
        return listStaffUseCase.list(command).stream()
                .map(Person::getLastname)
                .filter(lastname -> lastname.startsWith(MARK))
                .toList();
    }

    @Test
    void directorSeesActiveTeachersPreceptorsAndAdministrativesButNoStudents() {
        actAs("DIRECTOR");

        assertThat(lastnames(new ListStaffCommand(null, null, null)))
                .containsExactly(MARK + "A1", MARK + "A2", MARK + "A3");
    }

    @Test
    void administrativeSeesOnlyTeachersAndPreceptors() {
        actAs("ADMINISTRATIVO");

        assertThat(lastnames(new ListStaffCommand(null, null, null)))
                .containsExactly(MARK + "A1", MARK + "A2");
    }

    @Test
    void blankRoleFilterBehavesLikeNoFilter() {
        actAs("DIRECTOR");

        assertThat(lastnames(new ListStaffCommand("  ", null, null))).hasSize(3);
    }

    @Test
    void roleFilterNarrowsToThatVisibleRole() {
        actAs("DIRECTOR");

        assertThat(lastnames(new ListStaffCommand("PRECEPTOR", null, null))).containsExactly(MARK + "A2");
    }

    @Test
    void roleFilterOutsideTheVisibleRolesReturnsNobody() {
        actAs("ADMINISTRATIVO");

        assertThat(lastnames(new ListStaffCommand("ADMINISTRATIVO", null, null))).isEmpty();
    }

    @Test
    void roleFilterForANonStaffRoleReturnsNobody() {
        actAs("DIRECTOR");

        assertThat(lastnames(new ListStaffCommand("ALUMNO", null, null))).isEmpty();
    }

    @Test
    void anActorWithoutStaffManagementRolesSeesNobody() {
        actAs("DOCENTE");

        assertThat(lastnames(new ListStaffCommand(null, null, null))).isEmpty();
    }

    @Test
    void inactiveStatusListsOnlyWithdrawnStaff() {
        actAs("DIRECTOR");

        assertThat(lastnames(new ListStaffCommand(null, StatusType.INACTIVE, null))).containsExactly(MARK + "A5");
    }

    @Test
    void searchMatchesByNameIgnoringCaseAndAccents() {
        actAs("DIRECTOR");

        assertThat(lastnames(new ListStaffCommand(null, null, "cárla"))).containsExactly(MARK + "A3");
    }

    @Test
    void searchMatchesByDni() {
        actAs("DIRECTOR");

        assertThat(lastnames(new ListStaffCommand(null, null, "lstcova2"))).containsExactly(MARK + "A2");
    }
}
