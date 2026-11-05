package com.red_escuela.backend.billing.collections.usecase;

import com.red_escuela.backend.billing.collections.domain.CollectionReminderImpact;
import com.red_escuela.backend.billing.collections.domain.CollectionReminderImpactQuery;

public interface GetCollectionReminderImpactUseCase {
    CollectionReminderImpact getImpactOf(CollectionReminderImpactQuery query);
}
