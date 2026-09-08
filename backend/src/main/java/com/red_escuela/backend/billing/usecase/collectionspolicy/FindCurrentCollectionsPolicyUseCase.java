package com.red_escuela.backend.billing.usecase.collectionspolicy;

import com.red_escuela.backend.billing.entity.CollectionsPolicy;

public interface FindCurrentCollectionsPolicyUseCase {
    CollectionsPolicy findCurrent();
}
