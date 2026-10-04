package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.dto.CreateStaffRequest;
import com.red_escuela.backend.identity.dto.UpdateStaffRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.exception.RoleNotAssignableByActorException;
import com.red_escuela.backend.identity.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StaffUseCaseTest extends AbstractIntegrationTest {
    @Autowired
    private CreateStaffUseCase createStaffUseCase;

    @Autowired
    private UpdateStaffUseCase updateStaffUseCase;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsActor(String dni, String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person actor = new Person();
        actor.setName("Actor");
        actor.setLastname(dni);
        actor.setDni(dni);
        actor.setUsername(dni);
        actor.setEmail(dni + "@example.com");
        actor.setAddress(address);
        actor.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        actor.getRoles().add(role);
        entityManager.persist(actor);
        entityManager.flush();

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(actor.getId().toString(), null));
    }

    private CreateStaffRequest requestParaRol(String dni, String role) {
        return new CreateStaffRequest(
                "Nombre", "Apellido", dni + "@example.com", dni,
                null, null, null, null, null, null, null, role);
    }

    @Test
    void administrativeCreatingATeacherWorks() {
        authenticateAsActor("B0000001", "ADMINISTRATIVO");

        Person creado = createStaffUseCase.create(requestParaRol("B0000011", "DOCENTE")).person();

        assertThat(creado.getId()).isNotNull();
        assertThat(creado.getRoles()).extracting(Role::getName).containsExactly("DOCENTE");
    }

    @Test
    void administrativeCreatingAnAdministrativeFails() {
        authenticateAsActor("B0000002", "ADMINISTRATIVO");

        assertThatThrownBy(() -> createStaffUseCase.create(requestParaRol("B0000012", "ADMINISTRATIVO")))
                .isInstanceOf(RoleNotAssignableByActorException.class);
    }

    @Test
    void directorCreatingAnAdministrativeWorks() {
        authenticateAsActor("B0000003", "DIRECTOR");

        Person creado = createStaffUseCase.create(requestParaRol("B0000013", "ADMINISTRATIVO")).person();

        assertThat(creado.getId()).isNotNull();
        assertThat(creado.getRoles()).extracting(Role::getName).containsExactly("ADMINISTRATIVO");
    }

    @Test
    void directorCreatingATeacherFails() {
        authenticateAsActor("B0000004", "DIRECTOR");

        assertThatThrownBy(() -> createStaffUseCase.create(requestParaRol("B0000014", "DOCENTE")))
                .isInstanceOf(RoleNotAssignableByActorException.class);
    }

    @Test
    void directorCannotEditATeacherAndAdministrativeCan() {
        authenticateAsActor("B0000005", "ADMINISTRATIVO");
        Person docente = createStaffUseCase.create(requestParaRol("B0000015", "DOCENTE")).person();

        authenticateAsActor("B0000006", "DIRECTOR");
        UpdateStaffRequest edicion = new UpdateStaffRequest("nuevo@example.com", null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> updateStaffUseCase.update(new UpdateStaffCommand(docente.getId(), edicion)))
                .isInstanceOf(RoleNotAssignableByActorException.class);

        authenticateAsActor("B0000007", "ADMINISTRATIVO");
        Person editado = updateStaffUseCase.update(new UpdateStaffCommand(docente.getId(), edicion)).person();
        assertThat(editado.emailValue()).isEqualTo("nuevo@example.com");
    }
}
