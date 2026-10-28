package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.operation.AuthorizationRequestDescriber;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.ChangeRow;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DescriptionLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DetailBlock;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectTone;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.MoneyText;
import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.policy.ChangePriceCommand;
import com.red_escuela.backend.billing.pricing.repository.PriceRepository;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class ChangePriceAuthorizationRequestDescriber implements AuthorizationRequestDescriber<ChangePriceCommand> {
    private static final Logger log = LoggerFactory.getLogger(ChangePriceAuthorizationRequestDescriber.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String ABSENT = "—";
    private static final String UNCHANGED = "Sin cambio";
    private static final String NEW_VALUE = "Nuevo";

    private final PriceRepository priceRepository;
    private final Clock clock;

    public ChangePriceAuthorizationRequestDescriber(PriceRepository priceRepository, Clock clock) {
        this.priceRepository = priceRepository;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.CHANGE_PRICE;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorizationRequestDescription describe(ChangePriceCommand command, Long authorizationRequestId) {
        Price price = priceRepository.findById(command.priceId()).orElse(null);
        String subject = price == null ? "Precio" : price.getDescription();
        List<DescriptionLine> lines = new ArrayList<>();
        lines.add(new DescriptionLine("Precio", subject));
        lines.add(new DescriptionLine("Vigente desde", DAY.format(command.effectiveFrom())));
        if (command.changeReason() != null && !command.changeReason().isBlank()) {
            lines.add(new DescriptionLine("Motivo del cambio", command.changeReason().strip()));
        }
        List<EffectLine> effects = List.of(new EffectLine(EffectTone.WARNING,
                "El nuevo precio rige desde el " + DAY.format(command.effectiveFrom()) + ". Lo que ya se emitió conserva su precio."));
        return new AuthorizationRequestDescription("Cambio de precio: " + subject, null, null, lines, effects, changeBlockOf(price, command));
    }

    private DetailBlock changeBlockOf(Price price, ChangePriceCommand command) {
        if (price == null) {
            return DetailBlock.none();
        }
        try {
            return DetailBlock.change(rowsOf(price.findVersionEffectiveOn(LocalDate.now(clock)).orElse(null), command));
        } catch (RuntimeException e) {
            log.warn("Price change cannot be compared with the current price, describing it without the table: price={}", command.priceId(), e);
            return DetailBlock.none();
        }
    }

    private List<ChangeRow> rowsOf(PriceVersion current, ChangePriceCommand command) {
        NonNegativeMoney proposedBase = command.baseAmount();
        NonNegativeMoney proposedDiscount = command.marketingDiscount() == null ? NonNegativeMoney.zero(proposedBase.value().currency())
                : command.marketingDiscount().discountOn(proposedBase);
        NonNegativeMoney proposedFinal = proposedBase.minusNotBelowZero(proposedDiscount);
        List<ChangeRow> rows = new ArrayList<>();
        rows.add(moneyRow("Precio base", current == null ? null : current.getBaseAmount(), proposedBase));
        NonNegativeMoney currentDiscount = current == null ? null : current.marketingDiscountAmount();
        if (!proposedDiscount.value().isZero() || (currentDiscount != null && !currentDiscount.value().isZero())) {
            rows.add(moneyRow("Descuento de marketing", currentDiscount, proposedDiscount));
        }
        rows.add(moneyRow("Precio final", current == null ? null : current.finalAmount(), proposedFinal));
        return rows;
    }

    private static ChangeRow moneyRow(String label, NonNegativeMoney today, NonNegativeMoney proposed) {
        if (today == null) {
            return new ChangeRow(label, ABSENT, MoneyText.of(proposed.value()), NEW_VALUE);
        }
        Money difference = proposed.value().minus(today.value());
        String differenceText = difference.isZero() ? UNCHANGED : (difference.isNegative() ? "-" : "+") + MoneyText.of(difference.isNegative() ? Money.zero(difference.currency()).minus(difference) : difference);
        return new ChangeRow(label, MoneyText.of(today.value()), MoneyText.of(proposed.value()), differenceText);
    }
}
