package io.clueterra.questions.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@TestConfiguration(proxyBeanMethods = false)
public class ClockTestConfiguration {

    public static final Instant FIXED_INSTANT = Instant.parse("2026-01-02T03:04:05Z");

    @Bean
    @Primary
    Clock Clock() {
        return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    }
}
