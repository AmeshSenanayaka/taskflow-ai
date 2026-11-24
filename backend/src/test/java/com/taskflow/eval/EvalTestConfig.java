package com.taskflow.eval;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the system clock with one the harness controls. Every bean that injects {@code Clock} gets this one. */
@TestConfiguration
public class EvalTestConfig {

    @Bean
    @Primary
    public MutableClock evalClock() {
        return new MutableClock(Scenario.Context.DEFAULT.now());
    }
}
