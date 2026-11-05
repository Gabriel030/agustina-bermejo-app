package com.red_escuela.backend.platform.service;

import com.red_escuela.backend.shared.domain.BillingShutdownImpactPort;
import com.red_escuela.backend.platform.domain.FeatureTransitionPreview;
import com.red_escuela.backend.platform.domain.FeatureTransitionRequest;
import com.red_escuela.backend.platform.domain.IssuedTransitionConfirmation;
import com.red_escuela.backend.platform.entity.PlatformFeature;
import com.red_escuela.backend.platform.exception.FeatureAlreadyInRequestedStateException;
import com.red_escuela.backend.platform.exception.FeatureNotProvisionedException;
import com.red_escuela.backend.platform.exception.FeatureValueInvalidException;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.platform.repository.PlatformFeatureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingTransitionPreviewer {

    private final PlatformFeatureRepository featureRepository;
    private final BillingShutdownImpactPort impactPort;
    private final FeatureTransitionConfirmationSigner confirmationSigner;

    public BillingTransitionPreviewer(
            PlatformFeatureRepository featureRepository,
            BillingShutdownImpactPort impactPort,
            FeatureTransitionConfirmationSigner confirmationSigner) {
        this.featureRepository = featureRepository;
        this.impactPort = impactPort;
        this.confirmationSigner = confirmationSigner;
    }

    @Transactional(readOnly = true)
    public FeatureTransitionPreview previewTransition(FeatureTransitionRequest request) {
        FeatureCode code = request.code();
        BillingMode target = request.target();
        if (!code.hasMode()) {
            throw new FeatureValueInvalidException(code, "esta feature no tiene modo");
        }
        PlatformFeature feature = featureRepository.findById(code).orElseThrow(() -> new FeatureNotProvisionedException(code));
        BillingMode current = feature.getMode();
        if (current == target) {
            throw new FeatureAlreadyInRequestedStateException(code);
        }
        IssuedTransitionConfirmation confirmation = confirmationSigner.issueConfirmation(code, current, target, feature.getVersion());
        return new FeatureTransitionPreview(code, current, target, feature.getVersion(), impactPort.describeBillingShutdownImpact(),
                confirmation.token(), confirmation.expiresAt());
    }
}
