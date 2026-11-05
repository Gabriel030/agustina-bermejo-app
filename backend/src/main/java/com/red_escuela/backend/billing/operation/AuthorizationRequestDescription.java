package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.shared.domain.Money;

import java.util.List;
import java.util.Objects;

public record AuthorizationRequestDescription(String summary, String studentName, Money amount, List<DescriptionLine> requestLines, List<EffectLine> effectLines,
                                              DetailBlock detailBlock) {

    public AuthorizationRequestDescription {
        Objects.requireNonNull(summary, "summary");
        requestLines = requestLines == null ? List.of() : List.copyOf(requestLines);
        effectLines = effectLines == null ? List.of() : List.copyOf(effectLines);
        detailBlock = detailBlock == null ? DetailBlock.none() : detailBlock;
    }

    public record DescriptionLine(String label, String value) {
        public DescriptionLine {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(value, "value");
        }
    }

    public enum EffectTone {
        SUCCESS,
        WARNING,
        NEUTRAL
    }

    public record EffectLine(EffectTone tone, String text) {
        public EffectLine {
            Objects.requireNonNull(tone, "tone");
            Objects.requireNonNull(text, "text");
        }
    }

    public enum DetailBlockType {
        NONE,
        REFUND_PARTS,
        CHANGE
    }

    public record RefundPartLine(Long refundId, Long paymentId, String methodType, String status, Money amount, String destinationAccount, String destinationHolder) {
    }

    public record ChangeRow(String label, String today, String proposed, String difference) {
    }

    public record DetailBlock(DetailBlockType type, List<RefundPartLine> refundParts, List<ChangeRow> changeRows) {
        public DetailBlock {
            Objects.requireNonNull(type, "type");
            refundParts = refundParts == null ? List.of() : List.copyOf(refundParts);
            changeRows = changeRows == null ? List.of() : List.copyOf(changeRows);
        }

        public static DetailBlock none() {
            return new DetailBlock(DetailBlockType.NONE, List.of(), List.of());
        }

        public static DetailBlock refundParts(List<RefundPartLine> parts) {
            return new DetailBlock(DetailBlockType.REFUND_PARTS, parts, List.of());
        }

        public static DetailBlock change(List<ChangeRow> rows) {
            return new DetailBlock(DetailBlockType.CHANGE, List.of(), rows);
        }
    }
}
