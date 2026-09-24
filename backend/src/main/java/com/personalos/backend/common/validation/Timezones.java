package com.personalos.backend.common.validation;

import java.time.ZoneId;
import java.util.Set;

/** IANA timezone identifiers such as {@code Asia/Kolkata}. Offsets and abbreviations are not accepted. */
public final class Timezones {

    public static final String DEFAULT = "UTC";

    private static final Set<String> IDS = Set.copyOf(ZoneId.getAvailableZoneIds());

    private Timezones() {
    }

    public static boolean isValid(String id) {
        return id != null && IDS.contains(id);
    }

    /** Falls back to UTC for a missing or unknown identifier. */
    public static String orDefault(String id) {
        return isValid(id) ? id : DEFAULT;
    }
}
