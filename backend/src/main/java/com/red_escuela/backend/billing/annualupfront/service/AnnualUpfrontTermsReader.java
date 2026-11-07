package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontTerms;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontTermsVersion;
import com.red_escuela.backend.billing.annualupfront.exception.AnnualUpfrontTermsNotFoundException;
import com.red_escuela.backend.billing.annualupfront.repository.AnnualUpfrontTermsVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AnnualUpfrontTermsReader {
    private static final Pattern VERSION_LABEL = Pattern.compile("^v([1-9][0-9]{0,8})$");

    private final AnnualUpfrontTermsVersionRepository repository;

    public AnnualUpfrontTermsReader(AnnualUpfrontTermsVersionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AnnualUpfrontTermsView findCurrentTerms() {
        return AnnualUpfrontTermsView.of(requireLatest(), true);
    }

    @Transactional(readOnly = true)
    public AnnualUpfrontTermsView findTermsOfVersion(int versionNumber) {
        AnnualUpfrontTermsVersion latest = requireLatest();
        AnnualUpfrontTermsVersion found = repository.findByVersionNumber(versionNumber)
                .orElseThrow(() -> new AnnualUpfrontTermsNotFoundException("Versión de términos del pago anual no encontrada: " + versionNumber));
        return AnnualUpfrontTermsView.of(found, found.getVersionNumber().equals(latest.getVersionNumber()));
    }

    @Transactional(readOnly = true)
    public List<AnnualUpfrontTermsView> listTermsVersions() {
        List<AnnualUpfrontTermsVersion> versions = repository.findAllByOrderByVersionNumberDesc();
        Integer latestNumber = versions.get(0).getVersionNumber();
        return versions.stream().map(version -> AnnualUpfrontTermsView.of(version, version.getVersionNumber().equals(latestNumber))).toList();
    }

    @Transactional(readOnly = true)
    public AnnualUpfrontTerms requireCurrentTerms() {
        return requireLatest().toTerms();
    }

    @Transactional(readOnly = true)
    public Optional<AnnualUpfrontTerms> findPublishedTermsByLabel(String versionLabel) {
        if (versionLabel == null) {
            return Optional.empty();
        }
        Matcher matcher = VERSION_LABEL.matcher(versionLabel);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return repository.findByVersionNumber(Integer.parseInt(matcher.group(1))).map(AnnualUpfrontTermsVersion::toTerms);
    }

    private AnnualUpfrontTermsVersion requireLatest() {
        return repository.findFirstByOrderByVersionNumberDesc().orElseThrow(() -> new IllegalStateException("There is no published annual upfront terms version"));
    }
}
