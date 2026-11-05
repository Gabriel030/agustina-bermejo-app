package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.dto.WithdrawStudentRequest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.RefreshToken;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.entity.WithdrawalReason;
import com.red_escuela.backend.identity.repository.RefreshTokenRepository;
import com.red_escuela.backend.identity.usecase.auth.LoginCommand;
import com.red_escuela.backend.identity.usecase.auth.LoginUseCase;
import com.red_escuela.backend.identity.entity.StudentStatusChange;
import com.red_escuela.backend.identity.entity.StudentStatusChangeType;
import com.red_escuela.backend.identity.repository.StudentStatusChangeRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WithdrawStudentServiceTest extends AbstractIntegrationTest {
    @Autowired
    private WithdrawStudentUseCase withdrawStudentUseCase;

    @Autowired
    private LoginUseCase loginUseCase;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private StudentStatusChangeRepository studentStatusChangeRepository;

    private Student persistStudentWithPassword(String username, String rawPassword) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setPassword(passwordEncoder.encode(rawPassword));
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);

        Institution institution = new Institution();
        institution.setName("Instituto " + username);
        entityManager.persist(institution);

        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        entityManager.flush();
        return student;
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Person authenticateAsActor(String username) {
        Person actor = persistStudentWithPassword(username, "Correcta123").getPerson();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(actor.getId()), null));
        return actor;
    }

    @Test
    void withdrawingAStudentRevokesAllItsSessions() {
        authenticateAsActor("actorbaja");
        Student student = persistStudentWithPassword("alumnobaja", "Correcta123");
        loginUseCase.login(new LoginCommand("alumnobaja", "Correcta123", "127.0.0.1"));
        loginUseCase.login(new LoginCommand("alumnobaja", "Correcta123", "127.0.0.2"));

        withdrawStudentUseCase.withdraw(new WithdrawStudentCommand(
                student.getId(), new WithdrawStudentRequest(WithdrawalReason.ABANDONO, null)));

        List<RefreshToken> tokens = refreshTokenRepository.findByPerson(student.getPerson());
        assertThat(tokens).hasSize(2);
        assertThat(tokens).allMatch(RefreshToken::getRevoked);
    }

    @Test
    void withdrawRecordsWithdrawnAtAndStatusChangeHistory() {
        Person actor = authenticateAsActor("actorhistorial");
        Student student = persistStudentWithPassword("alumnohistorial", "Correcta123");

        withdrawStudentUseCase.withdraw(new WithdrawStudentCommand(
                student.getId(), new WithdrawStudentRequest(WithdrawalReason.ABANDONO, "dejo de cursar")));

        entityManager.flush();
        entityManager.clear();
        Student reloaded = entityManager.find(Student.class, student.getId());
        assertThat(reloaded.getWithdrawnAt()).isNotNull();
        List<StudentStatusChange> history = studentStatusChangeRepository
                .findByStudentIdOrderByOccurredAtDesc(student.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getChangeType()).isEqualTo(StudentStatusChangeType.WITHDRAWN);
        assertThat(history.get(0).getActorPersonId()).isEqualTo(actor.getId());
        assertThat(history.get(0).getReason()).isEqualTo("dejo de cursar");
        assertThat(history.get(0).getOccurredAt()).isNotNull();
    }
}
