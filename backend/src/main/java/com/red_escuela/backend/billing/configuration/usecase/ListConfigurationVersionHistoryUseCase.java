package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;

import java.util.List;

public interface ListConfigurationVersionHistoryUseCase {
    List<ConfigurationVersion> listVersionHistory(ConfigurationTopic topic);
}
