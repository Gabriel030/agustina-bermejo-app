package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.dto.ActivationInstitutionContact;
import com.red_escuela.backend.identity.dto.ActivationTokenStatusResponse;
import com.red_escuela.backend.identity.entity.ActivationToken;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.ActivationTokenRepository;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.specialist.PublicPasswordResetBlockPolicy;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ActivationTokenStatusReader {
    private final ActivationTokenRepository activationTokenRepository;
    private final PersonRepository personRepository;
    private final StudentRepository studentRepository;
    private final InstitutionRepository institutionRepository;
    private final PublicPasswordResetBlockPolicy publicPasswordResetBlockPolicy;

    public ActivationTokenStatusReader(ActivationTokenRepository activationTokenRepository,
                                       PersonRepository personRepository, StudentRepository studentRepository,
                                       InstitutionRepository institutionRepository,
                                       PublicPasswordResetBlockPolicy publicPasswordResetBlockPolicy) {
        this.activationTokenRepository = activationTokenRepository;
        this.personRepository = personRepository;
        this.studentRepository = studentRepository;
        this.institutionRepository = institutionRepository;
        this.publicPasswordResetBlockPolicy = publicPasswordResetBlockPolicy;
    }

    @Transactional(readOnly = true)
    public ActivationTokenStatusResponse readActivationTokenStatus(String clearToken) {
        if (clearToken == null || clearToken.isBlank()) {
            return ActivationTokenStatusResponse.invalid();
        }
        Optional<ActivationToken> token = activationTokenRepository.findByTokenHash(TokenHashUtil.sha256Hex(clearToken))
                .filter(found -> found.getUsedAt() == null);
        if (token.isEmpty()) {
            return ActivationTokenStatusResponse.invalid();
        }
        Optional<Person> person = personRepository.findById(token.get().getPerson().getId())
                .filter(found -> isRedeemableBy(found, token.get()));
        if (person.isEmpty()) {
            return ActivationTokenStatusResponse.invalid();
        }
        ActivationInstitutionContact institution = findInstitutionContact(person.get());
        if (!token.get().getExpiresAt().isAfter(ApplicationTime.now())) {
            return ActivationTokenStatusResponse.expired(institution);
        }
        return ActivationTokenStatusResponse.valid(loginIdentifierOf(person.get()), institution);
    }

    private boolean isRedeemableBy(Person person, ActivationToken token) {
        return person.getPassword() == null
                && person.getStatus() == StatusType.ACTIVE
                && token.getEmail().equalsIgnoreCase(person.emailValue())
                && !publicPasswordResetBlockPolicy.blocks(person);
    }

    private String loginIdentifierOf(Person person) {
        String username = person.getUsername();
        return username != null && !username.isBlank() ? username : person.emailValue();
    }

    private ActivationInstitutionContact findInstitutionContact(Person person) {
        Optional<Institution> institution = studentRepository.findByPerson_Id(person.getId())
                .map(student -> student.getInstitution())
                .or(() -> institutionRepository.findAll(PageRequest.of(0, 1, Sort.by("id"))).stream().findFirst());
        return institution.map(found -> new ActivationInstitutionContact(found.getName(), found.getEmail(), found.getPhoneNumber()))
                .orElse(null);
    }
}
