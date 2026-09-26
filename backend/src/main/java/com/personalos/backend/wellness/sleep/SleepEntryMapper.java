package com.personalos.backend.wellness.sleep;

import com.personalos.backend.wellness.sleep.domain.SleepEntry;
import com.personalos.backend.wellness.sleep.dto.SleepDtos.SleepEntryResponse;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class SleepEntryMapper {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    /** Shown in the zone the night was logged in, so a later change of profile timezone does not move it. */
    public SleepEntryResponse toResponse(SleepEntry entry) {
        return toResponse(entry.getSleepDate(), entry.getBedtimeAt(), entry.getWokeAt(), entry.getZoneId());
    }

    public SleepEntryResponse toResponse(LocalDate date, Instant bedtimeAt, Instant wokeAt, String zoneId) {
        ZoneId zone = ZoneId.of(zoneId);
        return new SleepEntryResponse(date, bedtimeAt.atZone(zone).toLocalDate(), clock(bedtimeAt, zone), clock(wokeAt, zone),
                bedtimeAt, wokeAt, zoneId, (int) Duration.between(bedtimeAt, wokeAt).toMinutes());
    }

    public static String clock(Instant instant, ZoneId zone) {
        return instant.atZone(zone).toLocalTime().format(CLOCK);
    }
}
