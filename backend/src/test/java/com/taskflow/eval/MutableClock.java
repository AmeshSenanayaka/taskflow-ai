package com.taskflow.eval;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A fixed clock that the harness moves to each scenario's "now", so dates and "overdue" are repeatable. */
public class MutableClock extends Clock {

    private volatile Instant instant;

    public MutableClock(Instant instant) {
        this.instant = instant;
    }

    public void set(Instant instant) {
        this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
