package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatoryDocumentType;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateContent;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
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
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

@Service
public class CertificateTemplatePublisher {

    private final CertificateTemplateFinder templateFinder;
    private final CertificateTemplateVersionRepository versionRepository;
    private final CertificateTemplateContentRules rules;
    private final CurrentActorFinder currentActorFinder;
    private final CertificateTemplateSignatories signatories;
    private final Clock clock;

    public CertificateTemplatePublisher(CertificateTemplateFinder templateFinder, CertificateTemplateVersionRepository versionRepository,
                                        CertificateTemplateContentRules rules, CurrentActorFinder currentActorFinder, CertificateTemplateSignatories signatories,
                                        Clock clock) {
        this.templateFinder = templateFinder;
        this.versionRepository = versionRepository;
        this.rules = rules;
        this.currentActorFinder = currentActorFinder;
        this.signatories = signatories;
        this.clock = clock;
    }

    @Transactional
    public CertificateTemplateVersionResponse publish(GraduationCertificateKind kind) {
        CertificateTemplate template = templateFinder.findForUpdate(kind);
        return publishDraftOf(template, content -> signatories.applyTo(template.getId(), content), rules::requirePublishable);
    }

    @Transactional
    public CertificateTemplateVersionResponse publish(DocumentTemplateType type) {
        CertificateTemplate template = templateFinder.findForUpdate(type);
        SignatoryDocumentType signatoryType = SignatoryDocumentType.valueOf(type.name());
        return publishDraftOf(template, content -> signatories.applyTo(template.getId(), content, signatoryType),
                rules::requirePublishableDocument);
    }

    private CertificateTemplateVersionResponse publishDraftOf(CertificateTemplate template,
                                                              UnaryOperator<CertificateTemplateContent> signatorySnapshot,
                                                              Consumer<CertificateTemplateContent> publishableRule) {
        CertificateTemplateVersion draft = versionRepository.findByTemplateIdAndStatus(template.getId(), TemplateVersionStatus.DRAFT)
                .orElseThrow(TemplateNothingToPublishException::new);
        CertificateTemplateVersion latest = versionRepository
                .findFirstByTemplateIdAndStatusOrderByVersionNumberDesc(template.getId(), TemplateVersionStatus.PUBLISHED)
                .orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        CertificateTemplateContent snapshot = signatorySnapshot.apply(draft.content());
        if (latest != null && latest.content().equals(snapshot)) {
            throw new TemplateNothingToPublishException();
        }
        publishableRule.accept(snapshot);
        draft.replaceContent(snapshot, now);
        Person actor = currentActorFinder.currentPerson();
        int number = latest == null ? 1 : latest.getVersionNumber() + 1;
        draft.publishAs(number, actor.getId(), actor.getLastname() + ", " + actor.getName(), now);
        return CertificateTemplateResponses.of(versionRepository.saveAndFlush(draft));
    }
}
