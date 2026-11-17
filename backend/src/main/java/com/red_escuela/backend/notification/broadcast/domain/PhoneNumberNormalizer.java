package com.red_escuela.backend.notification.broadcast.domain;

import java.util.Optional;
import java.util.regex.Pattern;

public final class PhoneNumberNormalizer {

    private static final Pattern ALLOWED_CHARACTERS = Pattern.compile("^\\+?[0-9 ()./-]+$");
    private static final Pattern NATIONAL_NUMBER = Pattern.compile("^(11|[23]\\d)\\d{8}$");
    private static final String COUNTRY_CODE = "54";
    private static final String MOBILE_MARKER = "9";
    private static final String TRUNK_PREFIX = "0";
    private static final String MOBILE_INFIX = "15";
    private static final int MIN_LENGTH_WITH_COUNTRY_CODE = 12;
    private static final int LENGTH_WITH_INFIX = 12;
    private static final int MIN_AREA_CODE_LENGTH = 2;
    private static final int MAX_AREA_CODE_LENGTH = 4;

    private PhoneNumberNormalizer() {
    }

    public static Optional<String> normalizeArgentineMobile(String rawNumber) {
        if (rawNumber == null || !ALLOWED_CHARACTERS.matcher(rawNumber.strip()).matches()) {
            return Optional.empty();
        }
        String national = withoutPrefixes(rawNumber.replaceAll("\\D", ""));
        return Optional.of(withoutMobileInfix(national))
                .filter(candidate -> NATIONAL_NUMBER.matcher(candidate).matches())
                .map(candidate -> "+" + COUNTRY_CODE + MOBILE_MARKER + candidate);
    }

    private static String withoutPrefixes(String digits) {
        String withoutCountry = digits.startsWith(COUNTRY_CODE) && digits.length() >= MIN_LENGTH_WITH_COUNTRY_CODE
                ? digits.substring(COUNTRY_CODE.length())
                : digits;
        String withoutMobileMarker = withoutCountry.startsWith(MOBILE_MARKER)
                ? withoutCountry.substring(MOBILE_MARKER.length())
                : withoutCountry;
        return withoutMobileMarker.startsWith(TRUNK_PREFIX)
                ? withoutMobileMarker.substring(TRUNK_PREFIX.length())
                : withoutMobileMarker;
    }

    private static String withoutMobileInfix(String national) {
        if (national.length() != LENGTH_WITH_INFIX) {
            return national;
        }
        for (int areaLength = MIN_AREA_CODE_LENGTH; areaLength <= MAX_AREA_CODE_LENGTH; areaLength++) {
            if (national.startsWith(MOBILE_INFIX, areaLength)) {
                return national.substring(0, areaLength) + national.substring(areaLength + MOBILE_INFIX.length());
            }
        }
        return national;
    }
}
