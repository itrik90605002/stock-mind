package com.warehouse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Cross-cutting infrastructure beans.
 *
 * <p>A {@link Clock} bean is injected wherever "now" is needed so that date-driven logic
 * (usage windows, forecasts) is deterministic and testable.
 */
@Configuration
@EnableScheduling
public class CoreConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
