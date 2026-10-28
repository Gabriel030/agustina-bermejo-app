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
import jakarta.persistence.EntityManager;
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

    @Test
    void withdrawingAStudentRevokesAllItsSessions() {
        Student student = persistStudentWithPassword("alumnobaja", "Correcta123");
        loginUseCase.login(new LoginCommand("alumnobaja", "Correcta123", "127.0.0.1"));
        loginUseCase.login(new LoginCommand("alumnobaja", "Correcta123", "127.0.0.2"));

        withdrawStudentUseCase.withdraw(new WithdrawStudentCommand(
                student.getId(), new WithdrawStudentRequest(WithdrawalReason.ABANDONO, null)));

        List<RefreshToken> tokens = refreshTokenRepository.findByPerson(student.getPerson());
        assertThat(tokens).hasSize(2);
        assertThat(tokens).allMatch(RefreshToken::getRevoked);
    }
}
