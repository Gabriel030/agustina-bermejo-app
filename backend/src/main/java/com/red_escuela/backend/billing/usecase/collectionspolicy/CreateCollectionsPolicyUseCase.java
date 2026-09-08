package com.red_escuela.backend.billing.usecase.collectionspolicy;

import com.red_escuela.backend.billing.dto.CreateCollectionsPolicyRequest;
import com.red_escuela.backend.billing.entity.CollectionsPolicy;

public interface CreateCollectionsPolicyUseCase {
    CollectionsPolicy create(CreateCollectionsPolicyRequest request);
}
