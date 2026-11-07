package com.red_escuela.backend.billing.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatusChange;
import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestInboxReader.InboxDetail;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DescriptionLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DetailBlock;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectLine;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AuthorizationRequestDetailResponse(AuthorizationRequestListItemResponse request, String reason, @Schema(nullable = true) AuthorizationRequestPersonResponse decidedBy, @Schema(nullable = true) String decisionReason,
                                                 List<DescriptionLine> requestLines, List<EffectLine> effectLines, DetailBlock detailBlock,
                                                 List<StatusChangeResponse> statusChanges) {

    public static AuthorizationRequestDetailResponse from(InboxDetail detail) {
        AuthorizationRequest request = detail.entry().request();
        AuthorizationRequestDescription description = detail.entry().description();
        Map<Long, String> names = detail.personNames();
        return new AuthorizationRequestDetailResponse(AuthorizationRequestListItemResponse.from(detail.entry(), names), request.getReason(),
                AuthorizationRequestPersonResponse.of(request.getDecidedBy(), names, List.of()), request.getDecisionReason(), description.requestLines(),
                description.effectLines(), description.detailBlock(), request.getStatusChanges().stream().map(change -> StatusChangeResponse.from(change, names)).toList());
    }

    public record StatusChangeResponse(@Schema(nullable = true) String fromStatus, String toStatus, String event, @Schema(nullable = true) Long actorPersonId, @Schema(nullable = true) String actorName, @Schema(nullable = true) String actorProcess, Instant changedAt,
                                       @Schema(nullable = true) String reason) {

        static StatusChangeResponse from(AuthorizationRequestStatusChange change, Map<Long, String> names) {
            return new StatusChangeResponse(change.getFromStatus() == null ? null : change.getFromStatus().name(), change.getToStatus().name(), change.getEvent().name(),
                    change.getActorPersonId(), change.getActorPersonId() == null ? null : names.get(change.getActorPersonId()), change.getActorProcess(), change.getChangedAt(),
                    change.getReason());
        }
    }
}
