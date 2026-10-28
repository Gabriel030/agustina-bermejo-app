package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.ChangeRow;
import com.red_escuela.backend.shared.domain.Percentage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class ConfigurationChangeRowFormats {
    static final String ABSENT = "—";
    static final String UNCHANGED = "Sin cambio";
    static final String NEW_VALUE = "Nuevo";
    static final String REMOVED_VALUE = "Se quita";
    static final String CHANGED_VALUE = "Cambia";

    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private ConfigurationChangeRowFormats() {
    }

    static ChangeRow buildCountRow(String label, Integer today, Integer proposed, String singular, String plural) {
        return new ChangeRow(label, countText(today, singular, plural), countText(proposed, singular, plural), countDifference(today, proposed, singular, plural));
    }

    static ChangeRow buildPercentageRow(String label, Percentage today, Percentage proposed) {
        return new ChangeRow(label, percentageText(today), percentageText(proposed), percentageDifference(today, proposed));
    }

    static ChangeRow buildFlagRow(String label, Boolean today, Boolean proposed) {
        return new ChangeRow(label, flagText(today), flagText(proposed), equalityDifference(today, proposed));
    }

    static ChangeRow buildDateRow(String label, LocalDate today, LocalDate proposed) {
        String difference = presenceDifference(today, proposed);
        if (difference == null) {
            long days = ChronoUnit.DAYS.between(today, proposed);
            difference = days == 0 ? UNCHANGED : signed(days) + (Math.abs(days) == 1 ? " día" : " días");
        }
        return new ChangeRow(label, today == null ? ABSENT : DAY.format(today), proposed == null ? ABSENT : DAY.format(proposed), difference);
    }

    static ChangeRow buildTimeRow(String label, LocalTime today, LocalTime proposed) {
        return new ChangeRow(label, today == null ? ABSENT : HOUR.format(today), proposed == null ? ABSENT : HOUR.format(proposed), equalityDifference(today, proposed));
    }

    static ChangeRow buildMonthRow(String label, Integer today, Integer proposed) {
        return new ChangeRow(label, monthText(today), monthText(proposed), equalityDifference(today, proposed));
    }

    static ChangeRow buildTextRow(String label, String today, String proposed) {
        return new ChangeRow(label, today == null ? ABSENT : today, proposed == null ? ABSENT : proposed, equalityDifference(today, proposed));
    }

    static ChangeRow buildListRow(String label, List<String> today, List<String> proposed) {
        return buildTextRow(label, today == null ? null : joined(today), proposed == null ? null : joined(proposed));
    }

    static String percentageText(Percentage value) {
        return value == null ? ABSENT : plain(value.fraction().movePointRight(2)) + " %";
    }

    static String joined(List<String> values) {
        return values.isEmpty() ? "Ninguno" : String.join(", ", values);
    }

    static String countText(Integer value, String singular, String plural) {
        if (value == null) {
            return ABSENT;
        }
        if (singular == null) {
            return String.valueOf(value);
        }
        return value + " " + (value == 1 ? singular : plural);
    }

    private static String countDifference(Integer today, Integer proposed, String singular, String plural) {
        String presence = presenceDifference(today, proposed);
        if (presence != null) {
            return presence;
        }
        int delta = proposed - today;
        if (delta == 0) {
            return UNCHANGED;
        }
        return singular == null ? signed(delta) : signed(delta) + " " + (Math.abs(delta) == 1 ? singular : plural);
    }

    private static String percentageDifference(Percentage today, Percentage proposed) {
        String presence = presenceDifference(today, proposed);
        if (presence != null) {
            return presence;
        }
        BigDecimal delta = proposed.fraction().subtract(today.fraction()).movePointRight(2);
        return delta.signum() == 0 ? UNCHANGED : signed(delta) + " pp";
    }

    private static String equalityDifference(Object today, Object proposed) {
        String presence = presenceDifference(today, proposed);
        if (presence != null) {
            return presence;
        }
        return Objects.equals(today, proposed) ? UNCHANGED : CHANGED_VALUE;
    }

    private static String presenceDifference(Object today, Object proposed) {
        if (today == null && proposed == null) {
            return UNCHANGED;
        }
        if (today == null) {
            return NEW_VALUE;
        }
        if (proposed == null) {
            return REMOVED_VALUE;
        }
        return null;
    }

    private static String flagText(Boolean value) {
        return value == null ? ABSENT : value ? "Sí" : "No";
    }

    private static String monthText(Integer month) {
        if (month == null) {
            return ABSENT;
        }
        String name = Month.of(month).getDisplayName(TextStyle.FULL, ARGENTINA);
        return name.substring(0, 1).toUpperCase(ARGENTINA) + name.substring(1);
    }

    private static String signed(long value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private static String signed(BigDecimal value) {
        String text = plain(value);
        return value.signum() > 0 ? "+" + text : text;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
