package com.personalos.backend.support;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests can set and advance, replacing the system clock in the Spring context. */
@TestComponent
@Primary
public class MutableClock extends Clock {

    private volatile Instant now = Instant.parse("2026-09-24T10:00:00Z");

    public void set(Instant instant) { this.now = instant; }

    public void advance(Duration duration) { this.now = now.plus(duration); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }

    @Override public Clock withZone(ZoneId zone) { return this; }

    @Override public Instant instant() { return now; }
}
