package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplateVersionStatus;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplate;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateVersion;
import com.red_escuela.backend.graduation.certificate.template.exception.TemplateNothingToPublishException;
import com.red_escuela.backend.graduation.certificate.template.repository.CertificateTemplateVersionRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class CertificateTemplatePublisher {

    private final CertificateTemplateFinder templateFinder;
    private final CertificateTemplateVersionRepository versionRepository;
    private final CertificateTemplateContentRules rules;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public CertificateTemplatePublisher(CertificateTemplateFinder templateFinder, CertificateTemplateVersionRepository versionRepository,
                                        CertificateTemplateContentRules rules, CurrentActorFinder currentActorFinder, Clock clock) {
        this.templateFinder = templateFinder;
        this.versionRepository = versionRepository;
        this.rules = rules;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    @Transactional
    public CertificateTemplateVersionResponse publish(GraduationCertificateKind kind) {
        CertificateTemplate template = templateFinder.findForUpdate(kind);
        CertificateTemplateVersion draft = versionRepository.findByTemplateIdAndStatus(template.getId(), TemplateVersionStatus.DRAFT)
                .orElseThrow(TemplateNothingToPublishException::new);
        CertificateTemplateVersion latest = versionRepository
                .findFirstByTemplateIdAndStatusOrderByVersionNumberDesc(template.getId(), TemplateVersionStatus.PUBLISHED)
                .orElse(null);
        if (latest != null && latest.content().equals(draft.content())) {
            throw new TemplateNothingToPublishException();
        }
        rules.requirePublishable(draft.content());
        Person actor = currentActorFinder.currentPerson();
        int number = latest == null ? 1 : latest.getVersionNumber() + 1;
        draft.publishAs(number, actor.getId(), actor.getLastname() + ", " + actor.getName(), LocalDateTime.now(clock));
        return CertificateTemplateResponses.of(versionRepository.saveAndFlush(draft));
    }
}
