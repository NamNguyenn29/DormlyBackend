package com.example.DormlyBackend.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public class TicketTestSupport {

    public static final Instant T0 = Instant.parse("2026-11-02T01:00:00Z");

    public static final UUID REPORTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID ADMIN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID STAFF_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID OTHER_STAFF_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    public static Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
