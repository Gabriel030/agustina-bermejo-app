package com.red_escuela.backend.platform.infrastructure;

import com.red_escuela.backend.platform.domain.InstituteSettingState;
import com.red_escuela.backend.platform.domain.InstituteSettingsSnapshot;
import com.red_escuela.backend.platform.entity.InstituteSetting;
import com.red_escuela.backend.platform.model.InstituteSettingCode;
import com.red_escuela.backend.platform.repository.InstituteSettingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;

@Component
public class InstituteSettingsSnapshotLoader {

    private final InstituteSettingRepository settingRepository;
    private final TransactionTemplate isolatedReadTransaction;
    private final Clock clock;

    public InstituteSettingsSnapshotLoader(InstituteSettingRepository settingRepository, PlatformTransactionManager transactionManager, Clock clock) {
        this.settingRepository = settingRepository;
        this.isolatedReadTransaction = new TransactionTemplate(transactionManager);
        this.isolatedReadTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.isolatedReadTransaction.setReadOnly(true);
        this.clock = clock;
    }

    public InstituteSettingsSnapshot loadCommittedSnapshot() {
        return isolatedReadTransaction.execute(status -> {
            Map<InstituteSettingCode, InstituteSettingState> states = new EnumMap<>(InstituteSettingCode.class);
            for (InstituteSetting setting : settingRepository.findAll()) {
                states.put(setting.getCode(), setting.toState());
            }
            return new InstituteSettingsSnapshot(states, clock.instant());
        });
    }
}
