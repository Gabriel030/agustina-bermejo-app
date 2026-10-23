package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ReminderMessage(ReminderMessageFormat format, ReminderMessageType leadMessageType, int priority, List<ReminderDigestLine> lines,
                              List<ReminderDigestLine> imputationOrderDebt, Map<Currency, NonNegativeMoney> creditByCurrency) {

    public ReminderMessage {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(leadMessageType, "leadMessageType");
        lines = List.copyOf(lines);
        imputationOrderDebt = List.copyOf(imputationOrderDebt);
        creditByCurrency = Map.copyOf(creditByCurrency);
    }

    public static Optional<ReminderMessage> compose(List<ReminderDigestLine> lines, List<ReminderDigestLine> imputationOrderDebt,
                                                  Map<Currency, NonNegativeMoney> creditByCurrency) {
        List<ReminderMessageType> messageTypes = lines.stream().flatMap(line -> line.notices().stream()).map(ReminderDigestNotice::messageType).toList();
        if (messageTypes.isEmpty()) {
            return Optional.empty();
        }
        ReminderMessageFormat format = formatOf(lines, messageTypes.size());
        ReminderMessageType lead = ReminderMessageSeverity.mostSevereOf(messageTypes);
        return Optional.of(new ReminderMessage(format, lead, ReminderMessageSeverity.dispatchPriorityOf(lead, format), lines, imputationOrderDebt, creditByCurrency));
    }

    private static ReminderMessageFormat formatOf(List<ReminderDigestLine> lines, int noticeCount) {
        boolean summarizesPriorDebt = lines.stream().flatMap(line -> line.notices().stream()).anyMatch(ReminderDigestNotice::summarizesPriorDebt);
        if (summarizesPriorDebt) {
            return ReminderMessageFormat.DEBT_SUMMARY;
        }
        return noticeCount == 1 && lines.size() == 1 ? ReminderMessageFormat.OWN_NOTICE : ReminderMessageFormat.DAILY_DIGEST;
    }

    public List<ReminderDigestLine> highlightedLines() {
        return lines.stream().filter(ReminderDigestLine::isHighlighted).toList();
    }

    public List<ReminderCurrencyBlock> blocks() {
        Map<Currency, List<ReminderDigestLine>> byCurrency = new LinkedHashMap<>();
        for (ReminderDigestLine line : lines) {
            byCurrency.computeIfAbsent(line.currency(), currency -> new ArrayList<>()).add(line);
        }
        return byCurrency.entrySet().stream()
                .map(entry -> ReminderCurrencyBlock.of(entry.getKey(), entry.getValue(), imputationOrderDebt.stream().filter(line -> line.currency() == entry.getKey()).toList(), creditByCurrency.getOrDefault(entry.getKey(), NonNegativeMoney.zero(entry.getKey()))))
                .toList();
    }

    public boolean emailPrecedesDueDate() {
        return format != ReminderMessageFormat.DEBT_SUMMARY && highlightedLines().stream().allMatch(ReminderDigestLine::precedesDueDate);
    }

    public Optional<ReminderMessage> withoutLinesPrecedingTheirDueDate() {
        if (format == ReminderMessageFormat.DEBT_SUMMARY) {
            return Optional.of(this);
        }
        return compose(lines.stream().filter(line -> !line.precedesDueDate()).toList(), imputationOrderDebt.stream().filter(line -> !line.precedesDueDate()).toList(), creditByCurrency);
    }
}
