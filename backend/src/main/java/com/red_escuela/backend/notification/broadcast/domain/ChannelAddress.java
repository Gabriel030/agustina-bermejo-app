package com.red_escuela.backend.notification.broadcast.domain;

import com.red_escuela.backend.shared.domain.EmailAddress;

import java.util.Objects;
import java.util.regex.Pattern;

public record ChannelAddress(Kind kind, String value) {

    public enum Kind {
        EMAIL,
        PHONE
    }

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    public ChannelAddress {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
        value = kind == Kind.EMAIL ? EmailAddress.of(value).value() : requireE164(value);
    }

    public static ChannelAddress ofEmail(String email) {
        return new ChannelAddress(Kind.EMAIL, email);
    }

    public static ChannelAddress ofPhone(String e164Phone) {
        return new ChannelAddress(Kind.PHONE, e164Phone);
    }

    private static String requireE164(String phone) {
        if (!E164.matcher(phone).matches()) {
            throw new IllegalArgumentException("A phone address is an E.164 number");
        }
        return phone;
    }
}
