package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.dto.CreateStaffRequest;
import com.red_escuela.backend.identity.dto.UpdateStaffRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.exception.RoleNotAssignableByActorException;
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

/**
 * RF-AD-05/RF-DI-04 (hallazgo de la revisión de matriz de permisos): antes de este fix no
 * existía ningún camino para dar de alta un usuario ADMINISTRATIVO — el Director tiene que
 * poder hacerlo, y el Administrativo tiene que seguir limitado a Docente/Preceptor.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StaffServiceTest extends AbstractIntegrationTest {

    @Autowired
    private StaffService staffService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void limpiarContextoDeSeguridad() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComoActor(String dni, String roleName) {
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
    void administrativoDaDeAltaUnDocenteFunciona() {
        autenticarComoActor("B0000001", "ADMINISTRATIVO");

        Person creado = staffService.createStaff(requestParaRol("B0000011", "DOCENTE"));

        assertThat(creado.getId()).isNotNull();
        assertThat(creado.getRoles()).extracting(Role::getName).containsExactly("DOCENTE");
    }

    @Test
    void administrativoIntentaDarDeAltaUnAdministrativoFalla() {
        autenticarComoActor("B0000002", "ADMINISTRATIVO");

        assertThatThrownBy(() -> staffService.createStaff(requestParaRol("B0000012", "ADMINISTRATIVO")))
                .isInstanceOf(RoleNotAssignableByActorException.class);
    }

    @Test
    void directorDaDeAltaUnAdministrativoFunciona() {
        autenticarComoActor("B0000003", "DIRECTOR");

        Person creado = staffService.createStaff(requestParaRol("B0000013", "ADMINISTRATIVO"));

        assertThat(creado.getId()).isNotNull();
        assertThat(creado.getRoles()).extracting(Role::getName).containsExactly("ADMINISTRATIVO");
    }

    @Test
    void directorIntentaDarDeAltaUnDocenteFalla() {
        autenticarComoActor("B0000004", "DIRECTOR");

        assertThatThrownBy(() -> staffService.createStaff(requestParaRol("B0000014", "DOCENTE")))
                .isInstanceOf(RoleNotAssignableByActorException.class);
    }

    @Test
    void directorNoPuedeEditarUnDocenteYAdministrativoSiPuede() {
        autenticarComoActor("B0000005", "ADMINISTRATIVO");
        Person docente = staffService.createStaff(requestParaRol("B0000015", "DOCENTE"));

        autenticarComoActor("B0000006", "DIRECTOR");
        UpdateStaffRequest edicion = new UpdateStaffRequest("nuevo@example.com", null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> staffService.updateStaff(docente.getId(), edicion))
                .isInstanceOf(RoleNotAssignableByActorException.class);

        autenticarComoActor("B0000007", "ADMINISTRATIVO");
        Person editado = staffService.updateStaff(docente.getId(), edicion);
        assertThat(editado.getEmail()).isEqualTo("nuevo@example.com");
    }
}
