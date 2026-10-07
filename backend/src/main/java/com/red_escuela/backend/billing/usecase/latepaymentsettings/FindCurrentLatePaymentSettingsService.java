package com.red_escuela.backend.billing.usecase.latepaymentsettings;

import java.time.Clock;
import com.red_escuela.backend.billing.entity.LatePaymentSettings;
import com.red_escuela.backend.billing.exception.NoActiveLatePaymentSettingsException;
import com.red_escuela.backend.billing.repository.LatePaymentSettingsRepository;
import com.red_escuela.backend.shared.UseCase;

import java.time.LocalDate;

@UseCase
public class FindCurrentLatePaymentSettingsService implements FindCurrentLatePaymentSettingsUseCase {

    private final LatePaymentSettingsRepository latePaymentSettingsRepository;
    private final Clock clock;

    public FindCurrentLatePaymentSettingsService(LatePaymentSettingsRepository latePaymentSettingsRepository, Clock clock) {
        this.latePaymentSettingsRepository = latePaymentSettingsRepository;
        this.clock = clock;
    }

    @Override
    public LatePaymentSettings findCurrent() {
        return latePaymentSettingsRepository.findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(LocalDate.now(clock))
                .orElseThrow(NoActiveLatePaymentSettingsException::new);
    }
}
